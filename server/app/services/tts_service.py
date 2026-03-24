from dataclasses import dataclass
from uuid import UUID


class TextToSpeechServiceError(Exception):
    """Raised when text-to-speech processing fails."""


@dataclass(frozen=True)
class TextToSpeechResult:
    audio_reply_url: str | None = None


class TextToSpeechService:
    def synthesize(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
        text: str,
    ) -> TextToSpeechResult:
        raise NotImplementedError


class MockTextToSpeechService(TextToSpeechService):
    def synthesize(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
        text: str,
    ) -> TextToSpeechResult:
        if not text.strip():
            raise TextToSpeechServiceError("Assistant text is empty.")

        # TODO: replace this mock with a provider-backed TTS call and storage URL.
        return TextToSpeechResult(
            audio_reply_url=f"mock://tts/{session_id}/{message_id}",
        )
