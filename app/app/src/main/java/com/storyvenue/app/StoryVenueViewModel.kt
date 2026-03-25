package com.storyvenue.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.storyvenue.app.voice.VoiceInterviewPhase
import java.io.File
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val PREFS_NAME = "storyvenue_prefs"
private const val KEY_SERVER_BASE_URL = "server_base_url"
private const val KEY_AUTH_SESSION = "auth_session"
private const val KEY_SELECTED_SESSION_ID = "selected_session_id"
private const val DEFAULT_SERVER_BASE_URL = "http://10.0.2.2:8000"

enum class AuthMode {
    SignIn,
    SignUp,
}

data class StoryVenueUiState(
    val serverBaseUrl: String = DEFAULT_SERVER_BASE_URL,
    val authMode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val authSession: AuthSession? = null,
    val authMessage: String? = null,
    val isAuthLoading: Boolean = false,
    val isRestoringSession: Boolean = true,
    val homeMessage: String? = null,
    val isHomeLoading: Boolean = false,
    val sessions: List<InterviewSessionSummary> = emptyList(),
    val selectedSessionId: String? = null,
    val newSessionTitle: String = "오늘의 인터뷰",
    val newSessionTheme: String = "",
    val phase: VoiceInterviewPhase = VoiceInterviewPhase.Idle,
    val lastAssistantQuestion: String = "assistant 응답이 오면 여기에 표시됩니다.",
    val transcriptPlaceholder: String = "아직 인식된 내용이 없습니다.",
    val helperText: String = "세션을 선택한 뒤 마이크 버튼을 눌러 말씀해 주세요.",
    val voiceErrorMessage: String? = null,
    val hasRecordAudioPermission: Boolean = false,
    val isPermissionDenied: Boolean = false,
    val currentRecordingPath: String? = null,
    val lastSavedRecordingPath: String? = null,
    val lastAssistantAudioUrl: String? = null,
    val pendingAssistantPlaybackUrl: String? = null,
    val isSessionEnded: Boolean = false,
    val consecutiveVoiceFailures: Int = 0,
    val messages: List<SessionMessage> = emptyList(),
    val chapterTypeInput: String = "childhood",
    val chapterInstructionInput: String = "",
    val chapters: List<ChapterDraft> = emptyList(),
    val selectedChapterId: String? = null,
    val chapterStatusMessage: String? = null,
    val isDraftLoading: Boolean = false,
    val bookTitleInput: String = "나의 이야기",
    val books: List<BookVersion> = emptyList(),
    val selectedBookId: String? = null,
    val compiledBook: BookVersion? = null,
    val bookStatusMessage: String? = null,
    val isBookLoading: Boolean = false,
    val feedQueryInput: String = "",
    val feedPosts: List<FeedPost> = emptyList(),
    val recommendedPeople: List<FeedPerson> = emptyList(),
    val selectedFeedPostId: String? = null,
    val selectedFeedPost: FeedPost? = null,
    val feedComments: List<FeedComment> = emptyList(),
    val feedCommentInput: String = "",
    val feedStatusMessage: String? = null,
    val isFeedLoading: Boolean = false,
    val chatRooms: List<ChatRoom> = emptyList(),
    val selectedChatRoomId: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatMessageInput: String = "",
    val chatStatusMessage: String? = null,
    val isChatLoading: Boolean = false,
    val pendingChatNavigationRoomId: String? = null,
)

class StoryVenueViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = StoryVenueBackendRepository()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var uiState by mutableStateOf(
        StoryVenueUiState(
            serverBaseUrl = prefs.getString(KEY_SERVER_BASE_URL, DEFAULT_SERVER_BASE_URL)
                ?: DEFAULT_SERVER_BASE_URL,
            authSession = loadStoredSession(),
            selectedSessionId = prefs.getString(KEY_SELECTED_SESSION_ID, null),
        )
    )
        private set

    init {
        restoreSession()
    }

    fun onServerBaseUrlChanged(value: String) {
        uiState = uiState.copy(
            serverBaseUrl = value,
            authMessage = null,
            homeMessage = null,
            feedStatusMessage = null,
            chatStatusMessage = null,
        )
        prefs.edit().putString(KEY_SERVER_BASE_URL, value).apply()
    }

    fun onAuthModeChanged(mode: AuthMode) {
        uiState = uiState.copy(
            authMode = mode,
            authMessage = null,
        )
    }

    fun onEmailChanged(value: String) {
        uiState = uiState.copy(
            email = value,
            authMessage = null,
        )
    }

    fun onPasswordChanged(value: String) {
        uiState = uiState.copy(
            password = value,
            authMessage = null,
        )
    }

    fun submitAuth() {
        if (uiState.isAuthLoading) {
            return
        }

        val email = uiState.email.trim()
        val password = uiState.password
        val baseUrl = uiState.serverBaseUrl.trim()

        if (baseUrl.isBlank()) {
            uiState = uiState.copy(authMessage = "서버 주소를 입력해 주세요.")
            return
        }
        if (email.isBlank() || password.isBlank()) {
            uiState = uiState.copy(authMessage = "이메일과 비밀번호를 모두 입력해 주세요.")
            return
        }

        uiState = uiState.copy(
            isAuthLoading = true,
            authMessage = null,
        )

        viewModelScope.launch {
            val result = when (uiState.authMode) {
                AuthMode.SignIn -> repository.signIn(baseUrl, email, password)
                AuthMode.SignUp -> repository.signUp(baseUrl, email, password)
            }

            result.fold(
                onSuccess = { payload ->
                    if (payload.session != null) {
                        saveAuthSession(payload.session)
                        uiState = uiState.copy(
                            authSession = payload.session,
                            isAuthLoading = false,
                            authMessage = payload.message,
                        )
                        loadSignedInData()
                    } else {
                        clearAuthSession(keepServerBaseUrl = true)
                        uiState = uiState.copy(
                            isAuthLoading = false,
                            authMessage = payload.message
                                ?: "가입이 완료되었습니다. 이메일 확인 후 로그인해 주세요.",
                        )
                    }
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isAuthLoading = false,
                        authMessage = error.message ?: "인증 요청에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun signOut() {
        clearAuthSession(keepServerBaseUrl = true)
        uiState = StoryVenueUiState(
            serverBaseUrl = prefs.getString(KEY_SERVER_BASE_URL, DEFAULT_SERVER_BASE_URL)
                ?: DEFAULT_SERVER_BASE_URL,
            authMode = AuthMode.SignIn,
            authMessage = "로그아웃되었습니다.",
            isRestoringSession = false,
        )
    }

    fun onNewSessionTitleChanged(value: String) {
        uiState = uiState.copy(
            newSessionTitle = value,
            homeMessage = null,
        )
    }

    fun onNewSessionThemeChanged(value: String) {
        uiState = uiState.copy(
            newSessionTheme = value,
            homeMessage = null,
        )
    }

    fun createSession() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(homeMessage = "먼저 로그인해 주세요.")
            return
        }
        val title = uiState.newSessionTitle.trim()
        if (title.isBlank()) {
            uiState = uiState.copy(homeMessage = "세션 제목을 입력해 주세요.")
            return
        }

        uiState = uiState.copy(
            isHomeLoading = true,
            homeMessage = null,
        )

        viewModelScope.launch {
            repository.createSession(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                title = title,
                theme = uiState.newSessionTheme.trim(),
            ).fold(
                onSuccess = { createdSession ->
                    val updatedSessions = listOf(createdSession) + uiState.sessions.filterNot {
                        it.id == createdSession.id
                    }
                    uiState = uiState.copy(
                        isHomeLoading = false,
                        sessions = updatedSessions,
                        selectedSessionId = createdSession.id,
                        newSessionTitle = "오늘의 인터뷰",
                        newSessionTheme = "",
                        homeMessage = "새 인터뷰 세션을 만들었습니다.",
                    )
                    prefs.edit().putString(KEY_SELECTED_SESSION_ID, createdSession.id).apply()
                    loadSelectedSessionData()
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isHomeLoading = false,
                        homeMessage = error.message ?: "세션 생성에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun selectSession(sessionId: String) {
        uiState = uiState.copy(
            selectedSessionId = sessionId,
            homeMessage = null,
        )
        prefs.edit().putString(KEY_SELECTED_SESSION_ID, sessionId).apply()
        loadSelectedSessionData()
    }

    fun onPermissionStateChecked(isGranted: Boolean) {
        uiState = uiState.copy(
            hasRecordAudioPermission = isGranted,
            isPermissionDenied = if (isGranted) false else uiState.isPermissionDenied,
        )
    }

    fun onPermissionRequestStarted() {
        uiState = uiState.copy(
            voiceErrorMessage = null,
            helperText = "마이크 권한을 요청합니다. 허용해 주시면 바로 녹음을 시작합니다.",
        )
    }

    fun onPermissionGranted() {
        uiState = uiState.copy(
            hasRecordAudioPermission = true,
            isPermissionDenied = false,
            voiceErrorMessage = null,
            helperText = "권한이 허용되었습니다. 마이크를 눌러 말씀해 주세요.",
        )
    }

    fun onPermissionDenied() {
        uiState = uiState.copy(
            hasRecordAudioPermission = false,
            isPermissionDenied = true,
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            voiceErrorMessage = "마이크 권한이 없어 녹음을 시작할 수 없습니다.",
            helperText = "마이크 권한이 없어 녹음을 시작할 수 없습니다. 다시 시도해 주세요.",
        )
    }

    fun onRecordingStarted(recordingPath: String, isRetry: Boolean) {
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Listening,
            isPermissionDenied = false,
            currentRecordingPath = recordingPath,
            transcriptPlaceholder = if (isRetry) {
                "다시 말하기를 듣는 중입니다."
            } else {
                uiState.transcriptPlaceholder
            },
            voiceErrorMessage = null,
            helperText = "듣는 중입니다. 말씀을 마치면 마이크 버튼을 한 번 더 눌러 주세요.",
        )
    }

    fun onRecordingStartFailed(message: String) {
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            voiceErrorMessage = message,
            helperText = message,
        )
    }

    fun onRecordingStopped(recordingPath: String) {
        val authSession = uiState.authSession ?: run {
            handleVoiceFailure("로그인 정보가 없습니다. 다시 로그인해 주세요.")
            return
        }
        val sessionId = uiState.selectedSessionId ?: run {
            handleVoiceFailure("먼저 인터뷰 세션을 선택해 주세요.")
            return
        }

        val recordingFile = File(recordingPath)
        if (!recordingFile.exists()) {
            handleVoiceFailure("녹음 파일을 찾을 수 없습니다.")
            return
        }

        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Transcribing,
            currentRecordingPath = null,
            lastSavedRecordingPath = recordingPath,
            voiceErrorMessage = null,
            helperText = "녹음 파일을 업로드하고 transcript 와 assistant 응답을 가져오는 중입니다.",
        )

        viewModelScope.launch {
            repository.uploadVoiceTurn(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                sessionId = sessionId,
                audioFile = recordingFile,
            ).fold(
                onSuccess = { result ->
                    val updatedMessages = (uiState.messages + result.assistantMessage).takeLast(8)
                    uiState = uiState.copy(
                        phase = VoiceInterviewPhase.Idle,
                        transcriptPlaceholder = result.transcript,
                        lastAssistantQuestion = result.assistantMessage.content,
                        lastAssistantAudioUrl = result.audioReplyUrl,
                        pendingAssistantPlaybackUrl = null,
                        voiceErrorMessage = null,
                        helperText = if (result.memoryItemsCreated > 0) {
                            "assistant 응답을 받았습니다. 기억 항목 ${result.memoryItemsCreated}개를 저장했습니다."
                        } else {
                            "assistant 응답을 받았습니다. 다시 듣기 버튼으로 재생할 수 있습니다."
                        },
                        consecutiveVoiceFailures = 0,
                        messages = updatedMessages,
                    )
                    loadMessagesForSelectedSession()
                },
                onFailure = { error ->
                    handleVoiceFailure(error.message ?: "음성 업로드에 실패했습니다.")
                },
            )
        }
    }

    fun onRecordingStopFailed(message: String) {
        handleVoiceFailure(message)
    }

    fun onRepeatLastQuestionRequested() {
        val authSession = uiState.authSession ?: run {
            handleVoiceFailure("로그인 정보가 없습니다. 다시 로그인해 주세요.")
            return
        }
        val sessionId = uiState.selectedSessionId ?: run {
            handleVoiceFailure("먼저 인터뷰 세션을 선택해 주세요.")
            return
        }

        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Responding,
            pendingAssistantPlaybackUrl = null,
            voiceErrorMessage = null,
            helperText = "마지막 assistant 질문을 다시 불러오는 중입니다.",
        )

        viewModelScope.launch {
            repository.repeatLastAssistant(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                sessionId = sessionId,
            ).fold(
                onSuccess = { result ->
                    uiState = uiState.copy(
                        phase = VoiceInterviewPhase.Idle,
                        lastAssistantQuestion = result.content,
                        lastAssistantAudioUrl = result.audioReplyUrl,
                        pendingAssistantPlaybackUrl = result.audioReplyUrl,
                        voiceErrorMessage = null,
                        helperText = "마지막 assistant 질문을 다시 불러왔습니다. 곧 재생합니다.",
                    )
                },
                onFailure = { error ->
                    handleVoiceFailure(error.message ?: "다시 듣기에 실패했습니다.")
                },
            )
        }
    }

    fun onAssistantPlaybackRequestConsumed() {
        uiState = uiState.copy(pendingAssistantPlaybackUrl = null)
    }

    fun onAssistantPlaybackStarted() {
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Playing,
            voiceErrorMessage = null,
            helperText = "assistant 오디오를 재생하는 중입니다.",
        )
    }

    fun onAssistantPlaybackCompleted() {
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            helperText = "재생이 끝났습니다. 필요하면 다시 듣기 또는 다시 말하기를 사용할 수 있습니다.",
        )
    }

    fun onAssistantPlaybackFailed(message: String) {
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            voiceErrorMessage = message,
            helperText = "오디오 재생에 실패했습니다. 서버 주소와 네트워크를 확인해 주세요.",
        )
    }

    fun onRetrySpeechReady() {
        uiState = uiState.copy(
            transcriptPlaceholder = "다시 말하기를 시작합니다. 아직 인식된 내용이 없습니다.",
            voiceErrorMessage = null,
            helperText = "다시 말하기를 준비했습니다. 마이크 권한이 있으면 바로 녹음을 시작합니다.",
        )
    }

    fun onEndSession() {
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            isSessionEnded = true,
            voiceErrorMessage = null,
            helperText = "세션 화면을 닫고 홈으로 돌아갑니다.",
        )
    }

    fun onSessionNavigationComplete() {
        uiState = uiState.copy(isSessionEnded = false)
    }

    fun onChapterTypeChanged(value: String) {
        uiState = uiState.copy(
            chapterTypeInput = value,
            chapterStatusMessage = null,
        )
    }

    fun onChapterInstructionChanged(value: String) {
        uiState = uiState.copy(
            chapterInstructionInput = value,
            chapterStatusMessage = null,
        )
    }

    fun generateChapter() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(chapterStatusMessage = "먼저 로그인해 주세요.")
            return
        }
        val sessionId = uiState.selectedSessionId ?: run {
            uiState = uiState.copy(chapterStatusMessage = "먼저 인터뷰 세션을 선택해 주세요.")
            return
        }
        val chapterType = uiState.chapterTypeInput.trim()
        if (chapterType.isBlank()) {
            uiState = uiState.copy(chapterStatusMessage = "장 주제를 입력해 주세요.")
            return
        }

        uiState = uiState.copy(
            isDraftLoading = true,
            chapterStatusMessage = null,
        )

        viewModelScope.launch {
            repository.generateChapter(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                sessionId = sessionId,
                chapterType = chapterType,
            ).fold(
                onSuccess = { chapter ->
                    val updated = uiState.chapters + chapter
                    uiState = uiState.copy(
                        isDraftLoading = false,
                        chapters = updated,
                        selectedChapterId = chapter.id,
                        chapterStatusMessage = "장 초안을 생성했습니다.",
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isDraftLoading = false,
                        chapterStatusMessage = error.message ?: "장 초안 생성에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun selectChapter(chapterId: String) {
        uiState = uiState.copy(
            selectedChapterId = chapterId,
            chapterStatusMessage = null,
        )
    }

    fun moveChapterUp(index: Int) {
        if (index <= 0 || index >= uiState.chapters.size) {
            return
        }
        val reordered = uiState.chapters.toMutableList()
        val current = reordered.removeAt(index)
        reordered.add(index - 1, current)
        uiState = uiState.copy(chapters = reordered)
    }

    fun moveChapterDown(index: Int) {
        if (index < 0 || index >= uiState.chapters.lastIndex) {
            return
        }
        val reordered = uiState.chapters.toMutableList()
        val current = reordered.removeAt(index)
        reordered.add(index + 1, current)
        uiState = uiState.copy(chapters = reordered)
    }

    fun reviseSelectedChapter() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(chapterStatusMessage = "먼저 로그인해 주세요.")
            return
        }
        val chapterId = uiState.selectedChapterId ?: run {
            uiState = uiState.copy(chapterStatusMessage = "먼저 수정할 장을 선택해 주세요.")
            return
        }
        val instruction = uiState.chapterInstructionInput.trim()
        if (instruction.isBlank()) {
            uiState = uiState.copy(chapterStatusMessage = "수정 요청을 입력해 주세요.")
            return
        }

        uiState = uiState.copy(
            isDraftLoading = true,
            chapterStatusMessage = null,
        )

        viewModelScope.launch {
            repository.reviseChapter(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                chapterId = chapterId,
                instruction = instruction,
            ).fold(
                onSuccess = { updated ->
                    replaceChapter(updated, "장 초안을 수정했습니다.")
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isDraftLoading = false,
                        chapterStatusMessage = error.message ?: "장 수정에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun regenerateSelectedChapter() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(chapterStatusMessage = "먼저 로그인해 주세요.")
            return
        }
        val chapterId = uiState.selectedChapterId ?: run {
            uiState = uiState.copy(chapterStatusMessage = "먼저 다시 생성할 장을 선택해 주세요.")
            return
        }

        uiState = uiState.copy(
            isDraftLoading = true,
            chapterStatusMessage = null,
        )

        viewModelScope.launch {
            repository.regenerateChapter(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                chapterId = chapterId,
            ).fold(
                onSuccess = { updated ->
                    replaceChapter(updated, "장 초안을 다시 생성했습니다.")
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isDraftLoading = false,
                        chapterStatusMessage = error.message ?: "장 재생성에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun onBookTitleChanged(value: String) {
        uiState = uiState.copy(
            bookTitleInput = value,
            bookStatusMessage = null,
        )
    }

    fun selectBook(bookId: String) {
        val book = uiState.books.firstOrNull { it.id == bookId } ?: return
        uiState = uiState.copy(
            selectedBookId = bookId,
            compiledBook = book,
            bookStatusMessage = null,
        )
    }

    fun compileBook() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(bookStatusMessage = "먼저 로그인해 주세요.")
            return
        }
        if (uiState.chapters.isEmpty()) {
            uiState = uiState.copy(bookStatusMessage = "먼저 장 초안을 하나 이상 준비해 주세요.")
            return
        }
        val title = uiState.bookTitleInput.trim()
        if (title.isBlank()) {
            uiState = uiState.copy(bookStatusMessage = "책 제목을 입력해 주세요.")
            return
        }

        uiState = uiState.copy(
            isBookLoading = true,
            bookStatusMessage = null,
        )

        viewModelScope.launch {
            repository.compileBook(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                title = title,
                chapterIds = uiState.chapters.map { it.id },
            ).fold(
                onSuccess = { book ->
                    val updatedBooks = listOf(book) + uiState.books.filterNot { it.id == book.id }
                    uiState = uiState.copy(
                        isBookLoading = false,
                        books = updatedBooks,
                        selectedBookId = book.id,
                        compiledBook = book,
                        bookStatusMessage = "최종 자서전 버전을 저장했습니다.",
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isBookLoading = false,
                        bookStatusMessage = error.message ?: "책 저장에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun onFeedQueryChanged(value: String) {
        uiState = uiState.copy(
            feedQueryInput = value,
            feedStatusMessage = null,
        )
    }

    fun onFeedCommentChanged(value: String) {
        uiState = uiState.copy(
            feedCommentInput = value,
            feedStatusMessage = null,
        )
    }

    fun refreshFeedRecommendations() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(feedStatusMessage = "먼저 로그인해 주세요.")
            return
        }

        uiState = uiState.copy(
            isFeedLoading = true,
            feedStatusMessage = null,
        )

        viewModelScope.launch {
            val feedResult = repository.listFeedPosts(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                queryText = uiState.feedQueryInput,
            )
            val peopleResult = repository.listRecommendedPeople(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                queryText = uiState.feedQueryInput,
            )

            val feedPosts = feedResult.getOrElse { emptyList() }
            val recommendedPeople = peopleResult.getOrElse { emptyList() }
            val selectedPost = uiState.selectedFeedPostId?.let { selectedId ->
                feedPosts.firstOrNull { it.id == selectedId } ?: uiState.selectedFeedPost
            }

            uiState = uiState.copy(
                isFeedLoading = false,
                feedPosts = feedPosts,
                recommendedPeople = recommendedPeople,
                selectedFeedPost = selectedPost,
                feedStatusMessage = feedResult.exceptionOrNull()?.message,
            )
        }
    }

    fun publishSelectedBookToFeed() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(feedStatusMessage = "먼저 로그인해 주세요.")
            return
        }
        val book = uiState.compiledBook
            ?: uiState.selectedBookId?.let { selectedId ->
                uiState.books.firstOrNull { it.id == selectedId }
            }
            ?: uiState.books.firstOrNull()
            ?: run {
                uiState = uiState.copy(feedStatusMessage = "먼저 저장된 자서전 버전을 하나 준비해 주세요.")
                return
            }

        uiState = uiState.copy(
            isFeedLoading = true,
            feedStatusMessage = null,
        )

        viewModelScope.launch {
            repository.publishBookToFeed(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                bookId = book.id,
            ).fold(
                onSuccess = { post ->
                    val updatedFeedPosts = listOf(post) + uiState.feedPosts.filterNot { it.id == post.id }
                    uiState = uiState.copy(
                        isFeedLoading = false,
                        feedPosts = updatedFeedPosts,
                        selectedFeedPostId = post.id,
                        selectedFeedPost = post,
                        feedComments = emptyList(),
                        feedStatusMessage = "선택한 자서전을 피드에 올렸습니다.",
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isFeedLoading = false,
                        feedStatusMessage = error.message ?: "피드 게시에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun selectFeedPost(postId: String) {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(feedStatusMessage = "먼저 로그인해 주세요.")
            return
        }

        uiState = uiState.copy(
            selectedFeedPostId = postId,
            isFeedLoading = true,
            feedStatusMessage = null,
        )

        viewModelScope.launch {
            val postResult = repository.getFeedPost(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                postId = postId,
            )
            val commentsResult = repository.listFeedComments(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                postId = postId,
            )

            postResult.fold(
                onSuccess = { post ->
                    uiState = uiState.copy(
                        isFeedLoading = false,
                        selectedFeedPost = post,
                        feedComments = commentsResult.getOrElse { emptyList() },
                        feedStatusMessage = commentsResult.exceptionOrNull()?.message,
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isFeedLoading = false,
                        feedStatusMessage = error.message ?: "피드 글을 불러오지 못했습니다.",
                    )
                },
            )
        }
    }

    fun submitFeedComment() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(feedStatusMessage = "먼저 로그인해 주세요.")
            return
        }
        val postId = uiState.selectedFeedPostId ?: run {
            uiState = uiState.copy(feedStatusMessage = "먼저 피드 글을 선택해 주세요.")
            return
        }
        val content = uiState.feedCommentInput.trim()
        if (content.isBlank()) {
            uiState = uiState.copy(feedStatusMessage = "댓글 내용을 입력해 주세요.")
            return
        }

        uiState = uiState.copy(
            isFeedLoading = true,
            feedStatusMessage = null,
        )

        viewModelScope.launch {
            repository.createFeedComment(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                postId = postId,
                content = content,
            ).fold(
                onSuccess = { comment ->
                    uiState = uiState.copy(
                        isFeedLoading = false,
                        feedComments = uiState.feedComments + comment,
                        feedCommentInput = "",
                        feedStatusMessage = "댓글을 남겼습니다.",
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isFeedLoading = false,
                        feedStatusMessage = error.message ?: "댓글 작성에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun recordFeedRead(
        postId: String,
        dwellSeconds: Int,
        completed: Boolean,
    ) {
        val authSession = uiState.authSession ?: return
        if (dwellSeconds <= 0 && !completed) {
            return
        }

        viewModelScope.launch {
            repository.recordFeedRead(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                postId = postId,
                dwellSeconds = dwellSeconds,
                completed = completed,
                queryText = uiState.feedQueryInput,
            ).onFailure { error ->
                uiState = uiState.copy(
                    feedStatusMessage = error.message ?: "읽기 기록 저장에 실패했습니다.",
                )
            }
        }
    }

    fun loadChatRooms() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(chatStatusMessage = "먼저 로그인해 주세요.")
            return
        }

        uiState = uiState.copy(
            isChatLoading = true,
            chatStatusMessage = null,
        )

        viewModelScope.launch {
            repository.listChatRooms(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
            ).fold(
                onSuccess = { rooms ->
                    val selectedRoomId = uiState.selectedChatRoomId
                        ?.takeIf { selectedId -> rooms.any { it.id == selectedId } }
                        ?: rooms.firstOrNull()?.id
                    uiState = uiState.copy(
                        isChatLoading = false,
                        chatRooms = rooms,
                        selectedChatRoomId = selectedRoomId,
                    )
                    if (selectedRoomId != null) {
                        loadMessagesForChatRoom(selectedRoomId)
                    } else {
                        uiState = uiState.copy(chatMessages = emptyList())
                    }
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isChatLoading = false,
                        chatStatusMessage = error.message ?: "채팅방 목록을 불러오지 못했습니다.",
                    )
                },
            )
        }
    }

    fun startChatWithUser(otherUserId: String) {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(chatStatusMessage = "먼저 로그인해 주세요.")
            return
        }

        uiState = uiState.copy(
            isChatLoading = true,
            chatStatusMessage = null,
        )

        viewModelScope.launch {
            repository.createChatRoom(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                otherUserId = otherUserId,
            ).fold(
                onSuccess = { room ->
                    val updatedRooms = listOf(room) + uiState.chatRooms.filterNot { it.id == room.id }
                    uiState = uiState.copy(
                        isChatLoading = false,
                        chatRooms = updatedRooms,
                        selectedChatRoomId = room.id,
                        pendingChatNavigationRoomId = room.id,
                    )
                    loadMessagesForChatRoom(room.id)
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isChatLoading = false,
                        chatStatusMessage = error.message ?: "채팅방을 준비하지 못했습니다.",
                    )
                },
            )
        }
    }

    fun selectChatRoom(roomId: String) {
        uiState = uiState.copy(
            selectedChatRoomId = roomId,
            chatStatusMessage = null,
        )
        loadMessagesForChatRoom(roomId)
    }

    fun onChatMessageChanged(value: String) {
        uiState = uiState.copy(
            chatMessageInput = value,
            chatStatusMessage = null,
        )
    }

    fun sendChatMessage() {
        val authSession = uiState.authSession ?: run {
            uiState = uiState.copy(chatStatusMessage = "먼저 로그인해 주세요.")
            return
        }
        val roomId = uiState.selectedChatRoomId ?: run {
            uiState = uiState.copy(chatStatusMessage = "먼저 채팅방을 선택해 주세요.")
            return
        }
        val content = uiState.chatMessageInput.trim()
        if (content.isBlank()) {
            uiState = uiState.copy(chatStatusMessage = "보낼 내용을 입력해 주세요.")
            return
        }

        uiState = uiState.copy(
            isChatLoading = true,
            chatStatusMessage = null,
        )

        viewModelScope.launch {
            repository.sendChatMessage(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                roomId = roomId,
                content = content,
            ).fold(
                onSuccess = { message ->
                    uiState = uiState.copy(
                        isChatLoading = false,
                        chatMessages = uiState.chatMessages + message,
                        chatMessageInput = "",
                    )
                    loadChatRooms()
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isChatLoading = false,
                        chatStatusMessage = error.message ?: "메시지 전송에 실패했습니다.",
                    )
                },
            )
        }
    }

    fun onPendingChatNavigationConsumed() {
        uiState = uiState.copy(pendingChatNavigationRoomId = null)
    }

    private fun restoreSession() {
        val storedSession = uiState.authSession
        if (storedSession == null) {
            uiState = uiState.copy(isRestoringSession = false)
            return
        }

        viewModelScope.launch {
            repository.getMe(
                baseUrl = uiState.serverBaseUrl,
                accessToken = storedSession.accessToken,
                existingRefreshToken = storedSession.refreshToken,
            ).fold(
                onSuccess = { restoredSession ->
                    saveAuthSession(restoredSession)
                    uiState = uiState.copy(
                        authSession = restoredSession,
                        isRestoringSession = false,
                        authMessage = null,
                    )
                    loadSignedInData()
                },
                onFailure = {
                    val refreshToken = storedSession.refreshToken
                    if (refreshToken.isNullOrBlank()) {
                        clearAuthSession(keepServerBaseUrl = true)
                        uiState = uiState.copy(isRestoringSession = false)
                        return@fold
                    }

                    repository.refreshSession(
                        baseUrl = uiState.serverBaseUrl,
                        refreshToken = refreshToken,
                    ).fold(
                        onSuccess = { payload ->
                            val refreshedSession = payload.session
                            if (refreshedSession == null) {
                                clearAuthSession(keepServerBaseUrl = true)
                                uiState = uiState.copy(isRestoringSession = false)
                            } else {
                                saveAuthSession(refreshedSession)
                                uiState = uiState.copy(
                                    authSession = refreshedSession,
                                    isRestoringSession = false,
                                    authMessage = payload.message,
                                )
                                loadSignedInData()
                            }
                        },
                        onFailure = {
                            clearAuthSession(keepServerBaseUrl = true)
                            uiState = uiState.copy(isRestoringSession = false)
                        },
                    )
                },
            )
        }
    }

    private fun loadSignedInData() {
        val authSession = uiState.authSession ?: return
        uiState = uiState.copy(isHomeLoading = true)

        viewModelScope.launch {
            val sessionsResult = repository.listSessions(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
            )
            val booksResult = repository.listBooks(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
            )

            val sessions = sessionsResult.getOrElse { emptyList() }
            val books = booksResult.getOrElse { emptyList() }
            val selectedSessionId = uiState.selectedSessionId
                ?.takeIf { id -> sessions.any { it.id == id } }
                ?: sessions.firstOrNull()?.id
            val selectedBook = uiState.selectedBookId
                ?.let { selectedId -> books.firstOrNull { it.id == selectedId } }
                ?: books.firstOrNull()

            uiState = uiState.copy(
                isAuthLoading = false,
                isRestoringSession = false,
                isHomeLoading = false,
                sessions = sessions,
                books = books,
                selectedBookId = selectedBook?.id,
                compiledBook = selectedBook,
                selectedSessionId = selectedSessionId,
                homeMessage = sessionsResult.exceptionOrNull()?.message,
            )

            if (selectedSessionId != null) {
                prefs.edit().putString(KEY_SELECTED_SESSION_ID, selectedSessionId).apply()
                loadSelectedSessionData()
            }
        }
    }

    private fun loadSelectedSessionData() {
        loadMessagesForSelectedSession()
        loadChaptersForSelectedSession()
    }

    private fun loadMessagesForSelectedSession() {
        val authSession = uiState.authSession ?: return
        val sessionId = uiState.selectedSessionId ?: run {
            uiState = uiState.copy(messages = emptyList())
            return
        }

        viewModelScope.launch {
            repository.listMessages(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                sessionId = sessionId,
            ).fold(
                onSuccess = { messages ->
                    val lastAssistant = messages.lastOrNull { it.role == "assistant" }
                    val lastUser = messages.lastOrNull { it.role == "user" }
                    uiState = uiState.copy(
                        messages = messages.takeLast(8),
                        lastAssistantQuestion = lastAssistant?.content
                            ?: "assistant 응답이 오면 여기에 표시됩니다.",
                        transcriptPlaceholder = lastUser?.content
                            ?: uiState.transcriptPlaceholder,
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        voiceErrorMessage = error.message ?: "메시지를 불러오지 못했습니다.",
                    )
                },
            )
        }
    }

    private fun loadChaptersForSelectedSession() {
        val authSession = uiState.authSession ?: return
        val sessionId = uiState.selectedSessionId ?: run {
            uiState = uiState.copy(chapters = emptyList(), selectedChapterId = null)
            return
        }

        viewModelScope.launch {
            repository.listChapters(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                sessionId = sessionId,
            ).fold(
                onSuccess = { chapters ->
                    val selectedChapterId = uiState.selectedChapterId
                        ?.takeIf { currentId -> chapters.any { it.id == currentId } }
                        ?: chapters.firstOrNull()?.id
                    uiState = uiState.copy(
                        chapters = chapters,
                        selectedChapterId = selectedChapterId,
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        chapterStatusMessage = error.message ?: "장 목록을 불러오지 못했습니다.",
                    )
                },
            )
        }
    }

    private fun loadMessagesForChatRoom(roomId: String) {
        val authSession = uiState.authSession ?: return

        viewModelScope.launch {
            repository.listChatMessages(
                baseUrl = uiState.serverBaseUrl,
                accessToken = authSession.accessToken,
                roomId = roomId,
            ).fold(
                onSuccess = { messages ->
                    uiState = uiState.copy(
                        chatMessages = messages,
                        isChatLoading = false,
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isChatLoading = false,
                        chatStatusMessage = error.message ?: "채팅 내용을 불러오지 못했습니다.",
                    )
                },
            )
        }
    }

    private fun replaceChapter(updated: ChapterDraft, successMessage: String) {
        val replaced = uiState.chapters.map { existing ->
            if (existing.id == updated.id) {
                updated
            } else {
                existing
            }
        }

        uiState = uiState.copy(
            isDraftLoading = false,
            chapters = replaced,
            selectedChapterId = updated.id,
            chapterInstructionInput = "",
            chapterStatusMessage = successMessage,
        )
    }

    private fun handleVoiceFailure(message: String) {
        val nextFailureCount = uiState.consecutiveVoiceFailures + 1
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            voiceErrorMessage = message,
            helperText = if (nextFailureCount >= 2) {
                "제가 잘 못 들었어요. 한 번만 더 천천히 말씀해 주세요. 화면의 텍스트 결과도 함께 확인해 주세요."
            } else {
                "업로드에 실패했습니다. 다시 말하기 또는 네트워크 상태 확인 후 다시 시도해 주세요."
            },
            consecutiveVoiceFailures = nextFailureCount,
        )
    }

    private fun saveAuthSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_AUTH_SESSION, serializeSession(session))
            .apply()
    }

    private fun clearAuthSession(keepServerBaseUrl: Boolean) {
        prefs.edit().remove(KEY_AUTH_SESSION).remove(KEY_SELECTED_SESSION_ID).apply()
        if (!keepServerBaseUrl) {
            prefs.edit().remove(KEY_SERVER_BASE_URL).apply()
        }
    }

    private fun loadStoredSession(): AuthSession? {
        val raw = prefs.getString(KEY_AUTH_SESSION, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            AuthSession(
                userId = json.getString("user_id"),
                email = json.optString("email").takeIf { it.isNotBlank() },
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                tokenType = json.optString("token_type").takeIf { it.isNotBlank() },
                expiresAt = json.optLong("expires_at").takeIf { it > 0L },
            )
        }.getOrNull()
    }

    private fun serializeSession(session: AuthSession): String {
        return JSONObject()
            .put("user_id", session.userId)
            .put("email", session.email ?: JSONObject.NULL)
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken ?: JSONObject.NULL)
            .put("token_type", session.tokenType ?: JSONObject.NULL)
            .put("expires_at", session.expiresAt ?: JSONObject.NULL)
            .toString()
    }
}
