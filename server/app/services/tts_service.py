from datetime import datetime, timedelta, timezone
from dataclasses import dataclass
from pathlib import Path
from uuid import UUID

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.core.config import get_settings


DEFAULT_TTS_INSTRUCTIONS = (
    "짧고 쉬운 한국어 존댓말로, 차분하고 친절한 톤으로, 너무 빠르지 않게 말해 주세요."
)


class TextToSpeechServiceError(Exception):
    """Raised when text-to-speech processing fails."""


class TextToSpeechConfigurationError(TextToSpeechServiceError):
    """Raised when text-to-speech configuration is missing."""


@dataclass(frozen=True)
class TextToSpeechResult:
    audio_reply_url: str
    audio_file_path: Path
    audio_format: str
    content_type: str


class TextToSpeechService:
    def synthesize(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
        text: str,
    ) -> TextToSpeechResult:
        raise NotImplementedError

    def get_or_synthesize(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
        text: str,
    ) -> TextToSpeechResult:
        return self.synthesize(
            session_id=session_id,
            message_id=message_id,
            text=text,
        )


class OpenAITextToSpeechService(TextToSpeechService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        voice: str | None = None,
        audio_format: str | None = None,
        public_path: str | None = None,
        output_dir: Path | None = None,
        instructions: str | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise TextToSpeechConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_tts_model
        self.voice = voice or settings.openai_tts_voice
        self.audio_format = audio_format or settings.openai_tts_format
        self.public_path = (public_path or settings.openai_tts_public_path).rstrip("/")
        self.output_dir = output_dir or settings.openai_tts_output_dir_path
        self.instructions = (
            instructions
            if instructions is not None
            else settings.openai_tts_instructions or DEFAULT_TTS_INSTRUCTIONS
        )
        self.retention_hours = settings.openai_tts_retention_hours
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def synthesize(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
        text: str,
    ) -> TextToSpeechResult:
        if not text.strip():
            raise TextToSpeechServiceError("Assistant text is empty.")

        self._cleanup_old_files()
        output_path = self._build_output_path(
            session_id=session_id,
            message_id=message_id,
        )

        try:
            with self.client.audio.speech.with_streaming_response.create(
                model=self.model,
                voice=self.voice,
                input=text,
                instructions=self.instructions,
                response_format=self.audio_format,
            ) as response:
                response.stream_to_file(output_path)
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
        ) as exc:
            raise TextToSpeechServiceError("OpenAI text-to-speech request failed.") from exc
        except Exception as exc:  # pragma: no cover - defensive fallback
            raise TextToSpeechServiceError("Unexpected text-to-speech failure.") from exc

        if not output_path.exists() or output_path.stat().st_size == 0:
            raise TextToSpeechServiceError("OpenAI text-to-speech output was empty.")

        return TextToSpeechResult(
            audio_reply_url=self._build_audio_reply_url(
                session_id=session_id,
                message_id=message_id,
            ),
            audio_file_path=output_path,
            audio_format=self.audio_format,
            content_type=_content_type_for_format(self.audio_format),
        )

    def get_or_synthesize(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
        text: str,
    ) -> TextToSpeechResult:
        self._cleanup_old_files()
        output_path = self._build_output_path(
            session_id=session_id,
            message_id=message_id,
        )
        if output_path.exists() and output_path.stat().st_size > 0:
            return TextToSpeechResult(
                audio_reply_url=self._build_audio_reply_url(
                    session_id=session_id,
                    message_id=message_id,
                ),
                audio_file_path=output_path,
                audio_format=self.audio_format,
                content_type=_content_type_for_format(self.audio_format),
            )

        return self.synthesize(
            session_id=session_id,
            message_id=message_id,
            text=text,
        )

    def _build_output_path(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
    ) -> Path:
        session_dir = self.output_dir / str(session_id)
        session_dir.mkdir(parents=True, exist_ok=True)
        return session_dir / f"{message_id}.{self.audio_format}"

    def _build_audio_reply_url(
        self,
        *,
        session_id: UUID,
        message_id: UUID,
    ) -> str:
        return f"{self.public_path}/{session_id}/{message_id}.{self.audio_format}"

    def _cleanup_old_files(self) -> None:
        if self.retention_hours <= 0:
            return

        cutoff = datetime.now(timezone.utc) - timedelta(hours=self.retention_hours)
        for path in self.output_dir.rglob(f"*.{self.audio_format}"):
            try:
                modified_at = datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc)
            except FileNotFoundError:
                continue

            if modified_at < cutoff:
                try:
                    path.unlink()
                except FileNotFoundError:
                    continue


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

        output_path = Path("/tmp") / f"{message_id}.mp3"

        # TODO: keep this only for tests or offline development paths.
        return TextToSpeechResult(
            audio_reply_url=f"mock://tts/{session_id}/{message_id}",
            audio_file_path=output_path,
            audio_format="mp3",
            content_type="audio/mpeg",
        )


def _content_type_for_format(audio_format: str) -> str:
    return {
        "mp3": "audio/mpeg",
        "wav": "audio/wav",
        "flac": "audio/flac",
        "opus": "audio/ogg",
        "pcm": "audio/pcm",
    }.get(audio_format, "application/octet-stream")
