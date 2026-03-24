from typing import Annotated
from uuid import UUID

from fastapi import Depends, Header, HTTPException, status

from app.core.config import get_settings
from app.services.auth_service import (
    AuthConfigurationError,
    AuthService,
    AuthUnauthorizedError,
    AuthenticatedUser,
)
from app.services.profile_service import (
    ProfileConfigurationError,
    ProfilePersistenceError,
    ProfileService,
)


def get_current_user(
    authorization: Annotated[str | None, Header()] = None,
    x_user_id: Annotated[str | None, Header(alias="X-User-Id")] = None,
) -> AuthenticatedUser:
    settings = get_settings()

    if authorization:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token.strip():
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authorization header must use Bearer token.",
            )

        try:
            authenticated_user = AuthService().get_user_from_access_token(token.strip())
            ProfileService().ensure_profile(
                user_id=authenticated_user.id,
                email=authenticated_user.email,
            )
            return authenticated_user
        except (AuthConfigurationError, ProfileConfigurationError) as exc:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Authentication service is not configured.",
            ) from exc
        except ProfilePersistenceError as exc:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to prepare user profile.",
            ) from exc
        except AuthUnauthorizedError as exc:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or expired access token.",
            ) from exc

    if settings.app_env == "local" and settings.allow_dev_user_header:
        if not x_user_id:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authorization Bearer token is required.",
            )

        try:
            user_id = UUID(x_user_id)
        except ValueError as exc:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="X-User-Id must be a valid UUID.",
            ) from exc

        try:
            ProfileService().ensure_profile(user_id=user_id, email=None)
        except (ProfileConfigurationError, ProfilePersistenceError) as exc:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to prepare local development profile.",
            ) from exc

        return AuthenticatedUser(
            id=user_id,
            email=None,
            access_token=None,
        )

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Authorization Bearer token is required.",
    )


def get_current_user_id(
    current_user: Annotated[AuthenticatedUser, Depends(get_current_user)],
) -> UUID:
    return current_user.id
