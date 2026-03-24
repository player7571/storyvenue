from uuid import UUID

from supabase import Client

from app.db.supabase import get_supabase_service_role_client


class ProfileServiceError(Exception):
    """Base error for profile persistence."""


class ProfileConfigurationError(ProfileServiceError):
    """Raised when profile service is not configured."""


class ProfilePersistenceError(ProfileServiceError):
    """Raised when profile rows cannot be created or updated."""


class ProfileService:
    def __init__(self, client: Client | None = None) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise ProfileConfigurationError(str(exc)) from exc

    def ensure_profile(
        self,
        *,
        user_id: UUID,
        email: str | None,
    ) -> None:
        payload = {
            "id": str(user_id),
            "email": email,
        }

        try:
            self.client.table("profiles").upsert(
                payload,
                on_conflict="id",
            ).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ProfilePersistenceError(str(exc)) from exc
