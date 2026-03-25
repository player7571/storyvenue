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

    def get_display_name(
        self,
        *,
        user_id: UUID,
    ) -> str:
        try:
            response = (
                self.client.table("profiles")
                .select("display_name, email")
                .eq("id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ProfilePersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            return "사용자"

        row = rows[0]
        display_name = (row.get("display_name") or "").strip()
        if display_name:
            return display_name

        email = (row.get("email") or "").strip()
        if not email:
            return "사용자"

        return email.split("@")[0]
