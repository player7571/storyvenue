from dataclasses import dataclass


class SpeechToTextServiceError(Exception):
    """Raised when speech-to-text processing fails."""


@dataclass(frozen=True)
class UploadedAudio:
    filename: str
    content_type: str | None
    mime_type: str | None
    language_hint: str | None
    data: bytes


@dataclass(frozen=True)
class SpeechToTextResult:
    transcript: str
    confidence: float | None = None


class SpeechToTextService:
    def transcribe(self, audio: UploadedAudio) -> SpeechToTextResult:
        raise NotImplementedError


class MockSpeechToTextService(SpeechToTextService):
    def transcribe(self, audio: UploadedAudio) -> SpeechToTextResult:
        if not audio.data:
            raise SpeechToTextServiceError("Uploaded audio is empty.")

        # TODO: replace this mock with a provider-backed STT call.
        return SpeechToTextResult(
            transcript="업로드된 음성의 임시 transcript 입니다.",
            confidence=None,
        )
