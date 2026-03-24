package com.storyvenue.app.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class VoiceTurnRequestConfig(
    val baseUrl: String,
    val userId: String,
    val sessionId: String,
    val languageHint: String = "ko",
    val mimeType: String = "audio/mp4",
)

data class VoiceTurnMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String?,
)

data class VoiceTurnResult(
    val userMessage: VoiceTurnMessage,
    val assistantMessage: VoiceTurnMessage,
    val transcript: String,
    val audioReplyUrl: String?,
)

data class RepeatLastAssistantResult(
    val assistantMessageId: String,
    val content: String,
    val audioReplyUrl: String,
)

class VoiceTurnRepository(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun uploadVoiceTurn(
        config: VoiceTurnRequestConfig,
        audioFile: File,
    ): Result<VoiceTurnResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!audioFile.exists()) {
                throw IOException("녹음 파일을 찾을 수 없습니다.")
            }

            val normalizedBaseUrl = config.baseUrl.trim().removeSuffix("/")
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", config.sessionId)
                .addFormDataPart("mime_type", config.mimeType)
                .addFormDataPart("language_hint", config.languageHint)
                .addFormDataPart(
                    name = "audio_file",
                    filename = audioFile.name,
                    body = audioFile.asRequestBody(config.mimeType.toMediaType()),
                )
                .build()

            val request = Request.Builder()
                .url("$normalizedBaseUrl/voice/turn")
                .header("X-User-Id", config.userId)
                .post(multipartBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(parseErrorMessage(responseBody))
                }

                parseVoiceTurnResult(
                    responseBody = responseBody,
                    normalizedBaseUrl = normalizedBaseUrl,
                )
            }
        }
    }

    suspend fun repeatLastAssistant(
        config: VoiceTurnRequestConfig,
    ): Result<RepeatLastAssistantResult> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedBaseUrl = config.baseUrl.trim().removeSuffix("/")
            val requestBody = JSONObject()
                .put("session_id", config.sessionId)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$normalizedBaseUrl/voice/repeat-last")
                .header("X-User-Id", config.userId)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(parseErrorMessage(responseBody))
                }

                parseRepeatLastAssistantResult(
                    responseBody = responseBody,
                    normalizedBaseUrl = normalizedBaseUrl,
                )
            }
        }
    }

    private fun parseVoiceTurnResult(
        responseBody: String,
        normalizedBaseUrl: String,
    ): VoiceTurnResult {
        val json = JSONObject(responseBody)
        val userMessage = parseMessage(
            messageJson = json.optJSONObject("user_message"),
            fallbackRole = "user",
        )
        val assistantMessage = parseMessage(
            messageJson = json.optJSONObject("assistant_message"),
            fallbackRole = "assistant",
        )
        val transcript = json.optString("transcript")
            .ifBlank { userMessage.content }
        val audioReplyUrl = json.optString("audio_reply_url")
            .takeIf { it.isNotBlank() }
            ?.let { rawUrl -> resolveAudioReplyUrl(normalizedBaseUrl, rawUrl) }

        if (transcript.isBlank()) {
            throw IOException("서버에서 transcript 를 받지 못했습니다.")
        }
        if (assistantMessage.content.isBlank()) {
            throw IOException("서버에서 assistant 응답을 받지 못했습니다.")
        }

        return VoiceTurnResult(
            userMessage = userMessage,
            assistantMessage = assistantMessage,
            transcript = transcript,
            audioReplyUrl = audioReplyUrl,
        )
    }

    private fun parseRepeatLastAssistantResult(
        responseBody: String,
        normalizedBaseUrl: String,
    ): RepeatLastAssistantResult {
        val json = JSONObject(responseBody)
        val assistantMessageId = json.optString("assistant_message_id")
        val content = json.optString("content")
        val rawAudioReplyUrl = json.optString("audio_reply_url")
        val audioReplyUrl = rawAudioReplyUrl
            .takeIf { it.isNotBlank() }
            ?.let { rawUrl -> resolveAudioReplyUrl(normalizedBaseUrl, rawUrl) }

        if (assistantMessageId.isBlank()) {
            throw IOException("서버에서 assistant message id 를 받지 못했습니다.")
        }
        if (content.isBlank()) {
            throw IOException("서버에서 마지막 assistant 텍스트를 받지 못했습니다.")
        }
        if (audioReplyUrl.isNullOrBlank()) {
            throw IOException("서버에서 재생 가능한 assistant 오디오를 받지 못했습니다.")
        }

        return RepeatLastAssistantResult(
            assistantMessageId = assistantMessageId,
            content = content,
            audioReplyUrl = audioReplyUrl,
        )
    }

    private fun parseMessage(
        messageJson: JSONObject?,
        fallbackRole: String,
    ): VoiceTurnMessage {
        if (messageJson == null) {
            return VoiceTurnMessage(
                id = "",
                role = fallbackRole,
                content = "",
                createdAt = null,
            )
        }

        return VoiceTurnMessage(
            id = messageJson.optString("id"),
            role = messageJson.optString("role").ifBlank { fallbackRole },
            content = messageJson.optString("content"),
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

    private fun resolveAudioReplyUrl(
        normalizedBaseUrl: String,
        rawUrl: String,
    ): String {
        return when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            rawUrl.startsWith("/") -> normalizedBaseUrl + rawUrl
            else -> "$normalizedBaseUrl/$rawUrl"
        }
    }
}
