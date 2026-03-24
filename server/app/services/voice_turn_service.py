from uuid import UUID, uuid4

from supabase import Client

from app.api.schemas.message import MessageResponse
from app.api.schemas.voice import VoiceTurnResponse
from app.db.supabase import get_supabase_service_role_client
from app.services.session_service import (
    SessionNotFoundError,
    SessionPersistenceError,
    SessionService,
)
from app.services.stt_service import (
    OpenAISpeechToTextService,
    SpeechToTextConfigurationError,
    SpeechToTextService,
    SpeechToTextServiceError,
    UploadedAudio,
)
from app.services.text_generation_service import (
    MockTextGenerationService,
    TextGenerationService,
    TextGenerationServiceError,
)
from app.services.tts_service import (
    OpenAITextToSpeechService,
    TextToSpeechConfigurationError,
    TextToSpeechService,
    TextToSpeechServiceError,
)


class VoiceTurnServiceError(Exception):
    """Base error for voice turn orchestration."""


class VoiceTurnConfigurationError(VoiceTurnServiceError):
    """Raised when the voice turn service is not configured."""


class VoiceTurnInputError(VoiceTurnServiceError):
    """Raised when the uploaded audio input is invalid."""


class VoiceTurnSessionNotFoundError(VoiceTurnServiceError):
    """Raised when the session does not exist for the current user."""


class VoiceTurnProcessingError(VoiceTurnServiceError):
    """Raised when the voice turn pipeline cannot complete."""


class VoiceTurnService:
    def __init__(
        self,
        client: Client | None = None,
        stt_service: SpeechToTextService | None = None,
        text_generation_service: TextGenerationService | None = None,
        tts_service: TextToSpeechService | None = None,
    ) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise VoiceTurnConfigurationError(str(exc)) from exc

        self.session_service = SessionService(client=self.client)
        try:
            self.stt_service = stt_service or OpenAISpeechToTextService()
        except SpeechToTextConfigurationError as exc:
            raise VoiceTurnConfigurationError(str(exc)) from exc
        self.text_generation_service = (
            text_generation_service or MockTextGenerationService()
        )
        try:
            self.tts_service = tts_service or OpenAITextToSpeechService()
        except TextToSpeechConfigurationError as exc:
            raise VoiceTurnConfigurationError(str(exc)) from exc

    def process_turn(
        self,
        *,
        user_id: UUID,
        session_id: UUID,
        audio: UploadedAudio,
    ) -> VoiceTurnResponse:
        if not audio.data:
            raise VoiceTurnInputError("Uploaded audio is empty.")

        try:
            self.session_service.get_session(user_id=user_id, session_id=session_id)
        except SessionNotFoundError as exc:
            raise VoiceTurnSessionNotFoundError(str(session_id)) from exc
        except SessionPersistenceError as exc:
            raise VoiceTurnProcessingError(str(exc)) from exc

        try:
            transcript_result = self.stt_service.transcribe(audio)
            user_message = self._create_message(
                user_id=user_id,
                session_id=session_id,
                role="user",
                content=transcript_result.transcript,
                source_type="stt",
                stt_confidence=transcript_result.confidence,
                safety_mode=False,
            )

            assistant_result = self.text_generation_service.generate_reply(
                session_id=session_id,
                transcript=transcript_result.transcript,
            )
            assistant_message = self._create_message(
                user_id=user_id,
                session_id=session_id,
                role="assistant",
                content=assistant_result.text,
                source_type="generated",
                safety_mode=assistant_result.safety_mode,
            )

            tts_result = self.tts_service.synthesize(
                session_id=session_id,
                message_id=assistant_message.id,
                text=assistant_message.content,
            )
        except (
            SpeechToTextServiceError,
            TextGenerationServiceError,
            TextToSpeechServiceError,
        ) as exc:
            raise VoiceTurnProcessingError(str(exc)) from exc

        return VoiceTurnResponse(
            user_message=user_message,
            assistant_message=assistant_message,
            transcript=transcript_result.transcript,
            audio_reply_url=tts_result.audio_reply_url,
            memory_items_created=0,
            safety_mode=assistant_result.safety_mode,
        )

    def _create_message(
        self,
        *,
        user_id: UUID,
        session_id: UUID,
        role: str,
        content: str,
        source_type: str,
        safety_mode: bool,
        stt_confidence: float | None = None,
    ) -> MessageResponse:
        message_id = uuid4()
        payload = {
            "id": str(message_id),
            "session_id": str(session_id),
            "user_id": str(user_id),
            "role": role,
            "content": content,
            "source_type": source_type,
            "safety_mode": safety_mode,
        }
        if stt_confidence is not None:
            payload["stt_confidence"] = stt_confidence

        try:
            self.client.table("messages").insert(payload).execute()
            response = (
                self.client.table("messages")
                .select("id, role, content, created_at")
                .eq("id", str(message_id))
                .eq("session_id", str(session_id))
                .eq("user_id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise VoiceTurnProcessingError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise VoiceTurnProcessingError("Created message could not be reloaded.")

        return MessageResponse.model_validate(rows[0])
