package com.storyvenue.app.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class VoiceInterviewPhase {
    Idle,
    Listening,
    Transcribing,
    Responding,
    Playing,
}

data class VoiceInterviewUiState(
    val phase: VoiceInterviewPhase = VoiceInterviewPhase.Idle,
    val serverBaseUrl: String = "http://10.0.2.2:8000",
    val userIdInput: String = "",
    val sessionIdInput: String = "",
    val lastAssistantQuestion: String = "assistant 응답이 오면 여기에 표시됩니다.",
    val transcriptPlaceholder: String = "아직 인식된 내용이 없습니다.",
    val helperText: String = "서버 주소, 사용자 ID, 세션 ID를 입력한 뒤 마이크 버튼을 눌러 말씀해 주세요.",
    val errorMessage: String? = null,
    val hasRecordAudioPermission: Boolean = false,
    val isPermissionDenied: Boolean = false,
    val currentRecordingPath: String? = null,
    val lastSavedRecordingPath: String? = null,
    val lastAssistantAudioUrl: String? = null,
    val pendingAssistantPlaybackUrl: String? = null,
    val isSessionEnded: Boolean = false,
)

class VoiceInterviewViewModel(
    private val voiceTurnRepository: VoiceTurnRepository = VoiceTurnRepository(),
) : ViewModel() {
    var uiState by mutableStateOf(VoiceInterviewUiState())
        private set

    private var flowJob: Job? = null

    fun onServerBaseUrlChanged(value: String) {
        uiState = uiState.copy(serverBaseUrl = value, errorMessage = null)
    }

    fun onUserIdChanged(value: String) {
        uiState = uiState.copy(userIdInput = value, errorMessage = null)
    }

    fun onSessionIdChanged(value: String) {
        uiState = uiState.copy(sessionIdInput = value, errorMessage = null)
    }

    fun onPermissionStateChecked(isGranted: Boolean) {
        uiState = uiState.copy(
            hasRecordAudioPermission = isGranted,
            isPermissionDenied = if (isGranted) false else uiState.isPermissionDenied,
        )
    }

    fun onPermissionRequestStarted() {
        uiState = uiState.copy(
            errorMessage = null,
            helperText = "마이크 권한을 요청합니다. 허용해 주시면 바로 녹음을 시작합니다.",
        )
    }

    fun onPermissionGranted() {
        uiState = uiState.copy(
            hasRecordAudioPermission = true,
            isPermissionDenied = false,
            errorMessage = null,
            helperText = "권한이 허용되었습니다. 마이크를 눌러 말씀해 주세요.",
        )
    }

    fun onPermissionDenied() {
        uiState = uiState.copy(
            hasRecordAudioPermission = false,
            isPermissionDenied = true,
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            errorMessage = "마이크 권한이 없어 녹음을 시작할 수 없습니다.",
            helperText = "마이크 권한이 없어 녹음을 시작할 수 없습니다. 다시 시도해 주세요.",
        )
    }

    fun onRecordingStarted(recordingPath: String, isRetry: Boolean) {
        flowJob?.cancel()
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Listening,
            isPermissionDenied = false,
            currentRecordingPath = recordingPath,
            transcriptPlaceholder = if (isRetry) {
                "다시 말하기를 듣는 중입니다."
            } else {
                "아직 인식된 내용이 없습니다."
            },
            errorMessage = null,
            helperText = "듣는 중입니다. 말씀을 마치면 마이크 버튼을 한 번 더 눌러 주세요.",
        )
    }

    fun onRecordingStartFailed(message: String) {
        flowJob?.cancel()
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            errorMessage = message,
            helperText = message,
        )
    }

    fun onRecordingStopped(recordingPath: String) {
        flowJob?.cancel()

        val uploadConfig = buildRequestConfig().getOrElse { error ->
            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Idle,
                currentRecordingPath = null,
                lastSavedRecordingPath = recordingPath,
                errorMessage = error.message,
                helperText = "업로드를 시작할 수 없습니다. 입력값을 확인해 주세요.",
            )
            return
        }

        val recordingFile = File(recordingPath)
        if (!recordingFile.exists()) {
            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Idle,
                currentRecordingPath = null,
                lastSavedRecordingPath = recordingPath,
                errorMessage = "녹음 파일을 찾을 수 없습니다.",
                helperText = "업로드를 시작할 수 없습니다. 녹음을 다시 시도해 주세요.",
            )
            return
        }

        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Transcribing,
            currentRecordingPath = null,
            lastSavedRecordingPath = recordingPath,
            errorMessage = null,
            helperText = "녹음 파일을 업로드하고 transcript 와 assistant 응답을 가져오는 중입니다.",
        )

        flowJob = viewModelScope.launch {
            voiceTurnRepository.uploadVoiceTurn(
                config = uploadConfig,
                audioFile = recordingFile,
            ).fold(
                onSuccess = { result ->
                    uiState = uiState.copy(
                        phase = VoiceInterviewPhase.Idle,
                        transcriptPlaceholder = result.transcript,
                        lastAssistantQuestion = result.assistantMessage.content,
                        lastAssistantAudioUrl = result.audioReplyUrl,
                        pendingAssistantPlaybackUrl = null,
                        errorMessage = null,
                        helperText = if (result.audioReplyUrl != null) {
                            "assistant 응답을 받았습니다. 다시 듣기 버튼으로 재생할 수 있습니다."
                        } else {
                            "assistant 응답을 받았지만 재생 가능한 오디오 응답이 없습니다."
                        },
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        phase = VoiceInterviewPhase.Idle,
                        errorMessage = error.message ?: "음성 업로드에 실패했습니다.",
                        helperText = "업로드에 실패했습니다. 서버 주소와 세션 정보를 확인해 주세요.",
                    )
                },
            )
        }
    }

    fun onRecordingStopFailed(message: String) {
        flowJob?.cancel()
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            errorMessage = message,
            helperText = message,
        )
    }

    fun onRepeatLastQuestionRequested() {
        flowJob?.cancel()

        val requestConfig = buildRequestConfig().getOrElse { error ->
            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Idle,
                pendingAssistantPlaybackUrl = null,
                errorMessage = error.message,
                helperText = "다시 듣기를 시작할 수 없습니다. 입력값을 확인해 주세요.",
            )
            return
        }

        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Responding,
            pendingAssistantPlaybackUrl = null,
            errorMessage = null,
            helperText = "마지막 assistant 질문을 다시 불러오는 중입니다.",
        )

        flowJob = viewModelScope.launch {
            voiceTurnRepository.repeatLastAssistant(
                config = requestConfig,
            ).fold(
                onSuccess = { result ->
                    uiState = uiState.copy(
                        phase = VoiceInterviewPhase.Idle,
                        lastAssistantQuestion = result.content,
                        lastAssistantAudioUrl = result.audioReplyUrl,
                        pendingAssistantPlaybackUrl = result.audioReplyUrl,
                        errorMessage = null,
                        helperText = "마지막 assistant 질문을 다시 불러왔습니다. 곧 재생합니다.",
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        phase = VoiceInterviewPhase.Idle,
                        pendingAssistantPlaybackUrl = null,
                        errorMessage = error.message ?: "다시 듣기에 실패했습니다.",
                        helperText = "다시 듣기에 실패했습니다. 세션과 네트워크 상태를 확인해 주세요.",
                    )
                },
            )
        }
    }

    fun onAssistantPlaybackRequestConsumed() {
        if (uiState.pendingAssistantPlaybackUrl == null) {
            return
        }

        uiState = uiState.copy(
            pendingAssistantPlaybackUrl = null,
        )
    }

    fun onAssistantPlaybackStarted() {
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Playing,
            errorMessage = null,
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
            errorMessage = message,
            helperText = "오디오 재생에 실패했습니다. 서버 주소와 네트워크를 확인해 주세요.",
        )
    }

    fun onRetrySpeechReady() {
        flowJob?.cancel()
        uiState = uiState.copy(
            transcriptPlaceholder = "다시 말하기를 시작합니다. 아직 인식된 내용이 없습니다.",
            errorMessage = null,
            helperText = "다시 말하기를 준비했습니다. 마이크 권한이 있으면 바로 녹음을 시작합니다.",
        )
    }

    fun onEndSession() {
        flowJob?.cancel()
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            currentRecordingPath = null,
            isSessionEnded = true,
            errorMessage = null,
            helperText = "세션을 종료하고 홈으로 돌아갑니다.",
        )
    }

    fun onSessionNavigationComplete() {
        uiState = uiState.copy(isSessionEnded = false)
    }

    private fun buildRequestConfig(): Result<VoiceTurnRequestConfig> {
        return runCatching {
            val baseUrl = uiState.serverBaseUrl.trim()
            val userId = uiState.userIdInput.trim()
            val sessionId = uiState.sessionIdInput.trim()

            require(baseUrl.isNotBlank()) {
                "서버 주소를 입력해 주세요."
            }
            require(userId.isNotBlank()) {
                "테스트용 사용자 ID를 입력해 주세요."
            }
            require(sessionId.isNotBlank()) {
                "세션 ID를 입력해 주세요."
            }

            UUID.fromString(userId)
            UUID.fromString(sessionId)

            VoiceTurnRequestConfig(
                baseUrl = baseUrl,
                userId = userId,
                sessionId = sessionId,
            )
        }
    }
}
