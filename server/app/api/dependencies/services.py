from fastapi import HTTPException, status

from app.services.session_service import SessionConfigurationError, SessionService


def get_session_service() -> SessionService:
    try:
        return SessionService()
    except SessionConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Supabase session service is not configured.",
        ) from exc
