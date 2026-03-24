package com.storyvenue.app.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val lastAssistantQuestion: String = "어릴 때 가장 먼저 떠오르는 장소를 말씀해 주세요.",
    val transcriptPlaceholder: String = "아직 인식된 내용이 없습니다.",
    val helperText: String = "마이크 버튼을 누르면 음성 인터뷰를 시작하는 흐름을 먼저 확인할 수 있습니다.",
    val isSessionEnded: Boolean = false,
)

class VoiceInterviewViewModel : ViewModel() {
    var uiState by mutableStateOf(VoiceInterviewUiState())
        private set

    private var flowJob: Job? = null

    fun onMicrophoneClick() {
        if (uiState.phase == VoiceInterviewPhase.Transcribing || uiState.phase == VoiceInterviewPhase.Responding) {
            return
        }

        if (uiState.phase == VoiceInterviewPhase.Listening) {
            simulateTranscriptionFlow()
            return
        }

        flowJob?.cancel()
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Listening,
            helperText = "듣는 중 placeholder 입니다. 다시 누르면 말하기를 마친 것으로 처리합니다.",
        )
    }

    fun onRepeatLastQuestion() {
        if (uiState.lastAssistantQuestion.isBlank()) return

        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Playing,
                helperText = "마지막 질문을 다시 들려주는 placeholder 흐름입니다.",
            )
            delay(900)
            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Idle,
                helperText = "다시 듣기가 끝났습니다. 필요하면 다시 말하기를 누르거나 마이크를 눌러 주세요.",
            )
        }
    }

    fun onRetrySpeech() {
        flowJob?.cancel()
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Listening,
            transcriptPlaceholder = "다시 말하기를 시작합니다. 아직 인식된 내용이 없습니다.",
            helperText = "다시 말하기 placeholder 입니다. 실제 녹음 연결 전 상태 흐름만 먼저 구현했습니다.",
        )
    }

    fun onEndSession() {
        flowJob?.cancel()
        uiState = uiState.copy(
            phase = VoiceInterviewPhase.Idle,
            isSessionEnded = true,
            helperText = "세션을 종료하고 홈으로 돌아갑니다.",
        )
    }

    fun onSessionNavigationComplete() {
        uiState = uiState.copy(isSessionEnded = false)
    }

    private fun simulateTranscriptionFlow() {
        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Transcribing,
                helperText = "변환 중 placeholder 입니다. 실제 STT 연결 전 단계입니다.",
            )
            delay(900)

            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Responding,
                transcriptPlaceholder = "초등학교 때는 여름마다 할머니 댁에 갔어요.",
                helperText = "답변하는 중 placeholder 입니다. AI 반응을 준비하는 흐름만 먼저 보여줍니다.",
            )
            delay(900)

            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Playing,
                lastAssistantQuestion = "그때 가장 반가웠던 사람은 누구였나요?",
                helperText = "음성 재생 중 placeholder 입니다. 실제 TTS 연결 전 단계입니다.",
            )
            delay(900)

            uiState = uiState.copy(
                phase = VoiceInterviewPhase.Idle,
                helperText = "다음 질문이 준비되었습니다. 필요하면 다시 듣기 또는 다시 말하기를 사용할 수 있습니다.",
            )
        }
    }
}
