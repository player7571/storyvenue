from dataclasses import dataclass
from io import BytesIO

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.core.config import get_settings


class SpeechToTextServiceError(Exception):
    """Raised when speech-to-text processing fails."""


class SpeechToTextConfigurationError(SpeechToTextServiceError):
    """Raised when speech-to-text configuration is missing."""


class SpeechToTextInputError(SpeechToTextServiceError):
    """Raised when the uploaded audio is invalid."""


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
    language: str | None = None
    model: str | None = None


class SpeechToTextService:
    def transcribe(self, audio: UploadedAudio) -> SpeechToTextResult:
        raise NotImplementedError


class OpenAISpeechToTextService(SpeechToTextService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        default_language: str | None = None,
        prompt: str | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise SpeechToTextConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_stt_model
        self.default_language = default_language or settings.openai_stt_language
        self.prompt = prompt if prompt is not None else settings.openai_stt_prompt

    def transcribe(self, audio: UploadedAudio) -> SpeechToTextResult:
        if not audio.data:
            raise SpeechToTextInputError("Uploaded audio is empty.")

        audio_buffer = BytesIO(audio.data)
        audio_buffer.name = audio.filename or "audio"
        language = audio.language_hint or self.default_language

        try:
            transcription = self.client.audio.transcriptions.create(
                file=audio_buffer,
                model=self.model,
                response_format="json",
                language=language,
                prompt=self.prompt,
            )
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
        ) as exc:
            raise SpeechToTextServiceError("OpenAI speech-to-text request failed.") from exc
        except Exception as exc:  # pragma: no cover - defensive fallback
            raise SpeechToTextServiceError("Unexpected speech-to-text failure.") from exc

        transcript = getattr(transcription, "text", None)
        if not transcript or not transcript.strip():
            raise SpeechToTextServiceError("OpenAI returned an empty transcript.")

        return SpeechToTextResult(
            transcript=transcript.strip(),
            confidence=None,
            language=language,
            model=self.model,
        )


class MockSpeechToTextService(SpeechToTextService):
    def transcribe(self, audio: UploadedAudio) -> SpeechToTextResult:
        if not audio.data:
            raise SpeechToTextInputError("Uploaded audio is empty.")

        # TODO: keep this only for tests or offline development paths.
        return SpeechToTextResult(
            transcript="업로드된 음성의 임시 transcript 입니다.",
            confidence=None,
            language=audio.language_hint,
            model="mock-stt",
        )
