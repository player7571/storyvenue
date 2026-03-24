package com.storyvenue.app

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AuthSession(
    val userId: String,
    val email: String?,
    val accessToken: String,
    val refreshToken: String?,
    val tokenType: String?,
    val expiresAt: Long?,
)

data class AuthResponsePayload(
    val session: AuthSession?,
    val requiresEmailConfirmation: Boolean,
    val message: String?,
)

data class InterviewSessionSummary(
    val id: String,
    val title: String,
    val theme: String?,
    val status: String,
    val createdAt: String?,
)

data class SessionMessage(
    val id: String,
    val role: String,
    val content: String,
    val safetyMode: Boolean,
    val createdAt: String?,
)

data class ChapterDraft(
    val id: String,
    val sessionId: String?,
    val chapterType: String?,
    val title: String,
    val content: String,
    val versionNo: Int,
    val createdAt: String?,
)

data class BookVersion(
    val id: String,
    val title: String,
    val content: String,
    val chapterIds: List<String>,
    val createdAt: String?,
)

data class VoiceTurnUploadResult(
    val transcript: String,
    val assistantMessage: SessionMessage,
    val audioReplyUrl: String?,
    val memoryItemsCreated: Int,
)

data class RepeatAssistantResult(
    val assistantMessageId: String,
    val content: String,
    val audioReplyUrl: String,
)

class StoryVenueBackendRepository(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun signIn(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<AuthResponsePayload> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${normalizeBaseUrl(baseUrl)}/auth/sign-in")
                .post(
                    JSONObject()
                        .put("email", email)
                        .put("password", password)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseAuthResponse(body)
            }
        }
    }

    suspend fun signUp(
        baseUrl: String,
        email: String,
        password: String,
    ): Result<AuthResponsePayload> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${normalizeBaseUrl(baseUrl)}/auth/sign-up")
                .post(
                    JSONObject()
                        .put("email", email)
                        .put("password", password)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseAuthResponse(body)
            }
        }
    }

    suspend fun refreshSession(
        baseUrl: String,
        refreshToken: String,
    ): Result<AuthResponsePayload> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${normalizeBaseUrl(baseUrl)}/auth/refresh")
                .post(
                    JSONObject()
                        .put("refresh_token", refreshToken)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseAuthResponse(body)
            }
        }
    }

    suspend fun getMe(
        baseUrl: String,
        accessToken: String,
        existingRefreshToken: String?,
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/auth/me", accessToken)
                .get()
                .build()

            execute(request) { body ->
                val parsed = parseAuthResponse(body)
                parsed.session?.copy(refreshToken = existingRefreshToken)
                    ?: throw IOException("서버에서 현재 로그인 정보를 확인하지 못했습니다.")
            }
        }
    }

    suspend fun listSessions(
        baseUrl: String,
        accessToken: String,
    ): Result<List<InterviewSessionSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/sessions", accessToken)
                .get()
                .build()

            execute(request) { body ->
                parseSessionList(body)
            }
        }
    }

    suspend fun createSession(
        baseUrl: String,
        accessToken: String,
        title: String,
        theme: String,
    ): Result<InterviewSessionSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/sessions", accessToken)
                .post(
                    JSONObject()
                        .put("title", title)
                        .put("theme", theme.ifBlank { JSONObject.NULL })
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseSession(JSONObject(body))
            }
        }
    }

    suspend fun listMessages(
        baseUrl: String,
        accessToken: String,
        sessionId: String,
    ): Result<List<SessionMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(
                baseUrl,
                "/messages/$sessionId",
                accessToken,
            ).get().build()

            execute(request) { body ->
                parseMessageList(body)
            }
        }
    }

    suspend fun uploadVoiceTurn(
        baseUrl: String,
        accessToken: String,
        sessionId: String,
        audioFile: File,
        languageHint: String = "ko",
        mimeType: String = "audio/mp4",
    ): Result<VoiceTurnUploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!audioFile.exists()) {
                throw IOException("녹음 파일을 찾을 수 없습니다.")
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart("mime_type", mimeType)
                .addFormDataPart("language_hint", languageHint)
                .addFormDataPart(
                    name = "audio_file",
                    filename = audioFile.name,
                    body = audioFile.asRequestBody(mimeType.toMediaType()),
                )
                .build()

            val request = authorizedRequestBuilder(baseUrl, "/voice/turn", accessToken)
                .post(multipartBody)
                .build()

            execute(request) { body ->
                parseVoiceTurnResult(body, normalizeBaseUrl(baseUrl))
            }
        }
    }

    suspend fun repeatLastAssistant(
        baseUrl: String,
        accessToken: String,
        sessionId: String,
    ): Result<RepeatAssistantResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/voice/repeat-last", accessToken)
                .post(
                    JSONObject()
                        .put("session_id", sessionId)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseRepeatAssistantResult(body, normalizeBaseUrl(baseUrl))
            }
        }
    }

    suspend fun listChapters(
        baseUrl: String,
        accessToken: String,
        sessionId: String,
    ): Result<List<ChapterDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(
                baseUrl,
                "/chapters?session_id=$sessionId",
                accessToken,
            ).get().build()

            execute(request) { body ->
                parseChapterList(body)
            }
        }
    }

    suspend fun generateChapter(
        baseUrl: String,
        accessToken: String,
        sessionId: String,
        chapterType: String,
    ): Result<ChapterDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/chapters/generate", accessToken)
                .post(
                    JSONObject()
                        .put("chapter_type", chapterType)
                        .put("session_id", sessionId)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseChapter(JSONObject(body))
            }
        }
    }

    suspend fun reviseChapter(
        baseUrl: String,
        accessToken: String,
        chapterId: String,
        instruction: String,
    ): Result<ChapterDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/chapters/$chapterId", accessToken)
                .patch(
                    JSONObject()
                        .put("instruction", instruction)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseChapter(JSONObject(body))
            }
        }
    }

    suspend fun regenerateChapter(
        baseUrl: String,
        accessToken: String,
        chapterId: String,
    ): Result<ChapterDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/chapters/$chapterId", accessToken)
                .patch(
                    JSONObject()
                        .put("regenerate", true)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseChapter(JSONObject(body))
            }
        }
    }

    suspend fun compileBook(
        baseUrl: String,
        accessToken: String,
        title: String,
        chapterIds: List<String>,
    ): Result<BookVersion> = withContext(Dispatchers.IO) {
        runCatching {
            val chapterIdArray = JSONArray()
            chapterIds.forEach { chapterIdArray.put(it) }

            val request = authorizedRequestBuilder(baseUrl, "/book/compile", accessToken)
                .post(
                    JSONObject()
                        .put("title", title)
                        .put("chapter_ids", chapterIdArray)
                        .toString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .build()

            execute(request) { body ->
                parseBook(JSONObject(body))
            }
        }
    }

    suspend fun listBooks(
        baseUrl: String,
        accessToken: String,
    ): Result<List<BookVersion>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorizedRequestBuilder(baseUrl, "/book/versions", accessToken)
                .get()
                .build()

            execute(request) { body ->
                parseBookList(body)
            }
        }
    }

    private fun authorizedRequestBuilder(
        baseUrl: String,
        path: String,
        accessToken: String,
    ): Request.Builder {
        return Request.Builder()
            .url(normalizeBaseUrl(baseUrl) + path)
            .header("Authorization", "Bearer $accessToken")
    }

    private fun <T> execute(
        request: Request,
        parser: (String) -> T,
    ): T {
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(parseErrorMessage(responseBody))
            }
            return parser(responseBody)
        }
    }

    private fun parseAuthResponse(responseBody: String): AuthResponsePayload {
        val json = JSONObject(responseBody)
        val session = parseAuthSession(json)
        return AuthResponsePayload(
            session = session,
            requiresEmailConfirmation = json.optBoolean("requires_email_confirmation", false),
            message = json.optString("message").takeIf { it.isNotBlank() },
        )
    }

    private fun parseAuthSession(json: JSONObject): AuthSession? {
        val user = json.optJSONObject("user") ?: return null
        val accessToken = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        return AuthSession(
            userId = user.optString("id"),
            email = user.optString("email").takeIf { it.isNotBlank() },
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
            tokenType = json.optString("token_type").takeIf { it.isNotBlank() },
            expiresAt = json.optLong("expires_at").takeIf { it > 0L },
        )
    }

    private fun parseSessionList(responseBody: String): List<InterviewSessionSummary> {
        val array = JSONArray(responseBody)
        return List(array.length()) { index ->
            parseSession(array.getJSONObject(index))
        }
    }

    private fun parseSession(json: JSONObject): InterviewSessionSummary {
        return InterviewSessionSummary(
            id = json.optString("id"),
            title = json.optString("title"),
            theme = json.optString("theme").takeIf { it.isNotBlank() },
            status = json.optString("status"),
            createdAt = json.optString("created_at").takeIf { it.isNotBlank() },
        )
    }

    private fun parseMessageList(responseBody: String): List<SessionMessage> {
        val array = JSONArray(responseBody)
        return List(array.length()) { index ->
            parseMessage(array.getJSONObject(index), "assistant")
        }
    }

    private fun parseVoiceTurnResult(
        responseBody: String,
        normalizedBaseUrl: String,
    ): VoiceTurnUploadResult {
        val json = JSONObject(responseBody)
        val transcript = json.optString("transcript")
        val assistantMessage = parseMessage(
            messageJson = json.optJSONObject("assistant_message"),
            fallbackRole = "assistant",
        )
        val audioReplyUrl = json.optString("audio_reply_url")
            .takeIf { it.isNotBlank() }
            ?.let { rawUrl -> resolveUrl(normalizedBaseUrl, rawUrl) }

        if (transcript.isBlank()) {
            throw IOException("서버에서 transcript 를 받지 못했습니다.")
        }
        if (assistantMessage.content.isBlank()) {
            throw IOException("서버에서 assistant 응답을 받지 못했습니다.")
        }

        return VoiceTurnUploadResult(
            transcript = transcript,
            assistantMessage = assistantMessage,
            audioReplyUrl = audioReplyUrl,
            memoryItemsCreated = json.optInt("memory_items_created", 0),
        )
    }

    private fun parseRepeatAssistantResult(
        responseBody: String,
        normalizedBaseUrl: String,
    ): RepeatAssistantResult {
        val json = JSONObject(responseBody)
        val assistantMessageId = json.optString("assistant_message_id")
        val content = json.optString("content")
        val audioReplyUrl = json.optString("audio_reply_url")
            .takeIf { it.isNotBlank() }
            ?.let { rawUrl -> resolveUrl(normalizedBaseUrl, rawUrl) }
            ?: throw IOException("서버에서 재생 가능한 assistant 오디오를 받지 못했습니다.")

        if (assistantMessageId.isBlank() || content.isBlank()) {
            throw IOException("서버에서 마지막 assistant 응답을 받지 못했습니다.")
        }

        return RepeatAssistantResult(
            assistantMessageId = assistantMessageId,
            content = content,
            audioReplyUrl = audioReplyUrl,
        )
    }

    private fun parseChapterList(responseBody: String): List<ChapterDraft> {
        val array = JSONArray(responseBody)
        return List(array.length()) { index ->
            parseChapter(array.getJSONObject(index))
        }
    }

    private fun parseChapter(json: JSONObject): ChapterDraft {
        return ChapterDraft(
            id = json.optString("chapter_id"),
            sessionId = json.optString("session_id").takeIf { it.isNotBlank() },
            chapterType = json.optString("chapter_type").takeIf { it.isNotBlank() },
            title = json.optString("title"),
            content = json.optString("content"),
            versionNo = json.optInt("version_no", 1),
            createdAt = json.optString("created_at").takeIf { it.isNotBlank() },
        )
    }

    private fun parseBookList(responseBody: String): List<BookVersion> {
        val array = JSONArray(responseBody)
        return List(array.length()) { index ->
            parseBook(array.getJSONObject(index))
        }
    }

    private fun parseBook(json: JSONObject): BookVersion {
        val chapterIdsJson = json.optJSONArray("chapter_ids") ?: JSONArray()
        val chapterIds = List(chapterIdsJson.length()) { index ->
            chapterIdsJson.optString(index)
        }.filter { it.isNotBlank() }

        return BookVersion(
            id = json.optString("book_id"),
            title = json.optString("title"),
            content = json.optString("content"),
            chapterIds = chapterIds,
            createdAt = json.optString("created_at").takeIf { it.isNotBlank() },
        )
    }

    private fun parseMessage(
        messageJson: JSONObject?,
        fallbackRole: String,
    ): SessionMessage {
        if (messageJson == null) {
            return SessionMessage(
                id = "",
                role = fallbackRole,
                content = "",
                safetyMode = false,
                createdAt = null,
            )
        }

        return SessionMessage(
            id = messageJson.optString("id"),
            role = messageJson.optString("role").ifBlank { fallbackRole },
            content = messageJson.optString("content"),
            safetyMode = messageJson.optBoolean("safety_mode", false),
            createdAt = messageJson.optString("created_at").takeIf { it.isNotBlank() },
        )
    }

    private fun parseErrorMessage(responseBody: String): String {
        if (responseBody.isBlank()) {
            return "서버 요청이 실패했습니다."
        }

        return runCatching {
            JSONObject(responseBody).optString("detail")
        }.getOrNull().takeIf { !it.isNullOrBlank() }
            ?: responseBody
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().removeSuffix("/")
    }

    private fun resolveUrl(
        normalizedBaseUrl: String,
        rawUrl: String,
    ): String {
        return when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            rawUrl.startsWith("/") -> normalizedBaseUrl + rawUrl
            else -> "$normalizedBaseUrl/$rawUrl"
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
