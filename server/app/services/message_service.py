from uuid import UUID

from supabase import Client

from app.api.schemas.message import MessageResponse
from app.db.supabase import get_supabase_service_role_client


class MessageServiceError(Exception):
    """Base error for message service."""


class MessageConfigurationError(MessageServiceError):
    """Raised when the message service is not configured."""


class MessagePersistenceError(MessageServiceError):
    """Raised when the message service cannot fetch data."""


class MessageSessionNotFoundError(MessageServiceError):
    """Raised when the session does not exist for the current user."""


class MessageService:
    def __init__(self, client: Client | None = None) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise MessageConfigurationError(str(exc)) from exc

    def list_messages(
        self,
        user_id: UUID,
        session_id: UUID,
    ) -> list[MessageResponse]:
        self._ensure_session_exists(user_id=user_id, session_id=session_id)

        try:
            response = (
                self.client.table("messages")
                .select("id, role, content, safety_mode, created_at")
                .eq("session_id", str(session_id))
                .eq("user_id", str(user_id))
                .order("created_at")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise MessagePersistenceError(str(exc)) from exc

        rows = response.data or []
        return [MessageResponse.model_validate(row) for row in rows]

    def _ensure_session_exists(self, user_id: UUID, session_id: UUID) -> None:
        try:
            response = (
                self.client.table("sessions")
                .select("id")
                .eq("id", str(session_id))
                .eq("user_id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise MessagePersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise MessageSessionNotFoundError(str(session_id))
