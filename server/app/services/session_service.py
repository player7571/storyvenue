from uuid import UUID, uuid4

from supabase import Client

from app.api.schemas.session import SessionCreateRequest, SessionResponse
from app.db.supabase import get_supabase_service_role_client


class SessionServiceError(Exception):
    """Base error for session service."""


class SessionConfigurationError(SessionServiceError):
    """Raised when the session service is not configured."""


class SessionPersistenceError(SessionServiceError):
    """Raised when the session service cannot persist or fetch data."""


class SessionNotFoundError(SessionServiceError):
    """Raised when a session does not exist for the current user."""


class SessionService:
    def __init__(self, client: Client | None = None) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise SessionConfigurationError(str(exc)) from exc

    def create_session(
        self,
        user_id: UUID,
        request: SessionCreateRequest,
    ) -> SessionResponse:
        session_id = uuid4()
        payload = {
            "id": str(session_id),
            "user_id": str(user_id),
            "title": request.title,
            "theme": request.theme,
            "status": "active",
        }

        try:
            self.client.table("sessions").insert(payload).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise SessionPersistenceError(str(exc)) from exc

        try:
            return self.get_session(user_id=user_id, session_id=session_id)
        except SessionNotFoundError as exc:
            raise SessionPersistenceError("Created session could not be reloaded.") from exc

    def get_session(self, user_id: UUID, session_id: UUID) -> SessionResponse:
        try:
            response = (
                self.client.table("sessions")
                .select("id, user_id, title, theme, status, created_at")
                .eq("id", str(session_id))
                .eq("user_id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise SessionPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise SessionNotFoundError(str(session_id))

        return SessionResponse.model_validate(rows[0])
