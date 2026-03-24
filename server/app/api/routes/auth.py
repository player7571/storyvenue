from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user
from app.api.schemas.auth import (
    AuthCredentialsRequest,
    AuthRefreshRequest,
    AuthSessionResponse,
)
from app.services.auth_service import (
    AuthConfigurationError,
    AuthInvalidCredentialsError,
    AuthService,
    AuthServiceError,
    AuthUnauthorizedError,
    AuthenticatedUser,
)

router = APIRouter(prefix="/auth", tags=["auth"])


def get_auth_service() -> AuthService:
    try:
        return AuthService()
    except AuthConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Supabase auth service is not configured.",
        ) from exc


@router.post(
    "/sign-up",
    response_model=AuthSessionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Sign up with email and password",
)
def sign_up(
    request: AuthCredentialsRequest,
    auth_service: Annotated[AuthService, Depends(get_auth_service)],
) -> AuthSessionResponse:
    try:
        return auth_service.sign_up(request)
    except AuthInvalidCredentialsError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except AuthServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to sign up.",
        ) from exc


@router.post(
    "/sign-in",
    response_model=AuthSessionResponse,
    summary="Sign in with email and password",
)
def sign_in(
    request: AuthCredentialsRequest,
    auth_service: Annotated[AuthService, Depends(get_auth_service)],
) -> AuthSessionResponse:
    try:
        return auth_service.sign_in(request)
    except AuthInvalidCredentialsError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(exc),
        ) from exc
    except AuthServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to sign in.",
        ) from exc


@router.post(
    "/refresh",
    response_model=AuthSessionResponse,
    summary="Refresh auth session",
)
def refresh_session(
    request: AuthRefreshRequest,
    auth_service: Annotated[AuthService, Depends(get_auth_service)],
) -> AuthSessionResponse:
    try:
        return auth_service.refresh(request.refresh_token)
    except AuthUnauthorizedError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(exc),
        ) from exc
    except AuthServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to refresh session.",
        ) from exc


@router.get(
    "/me",
    response_model=AuthSessionResponse,
    summary="Get current authenticated user",
)
def get_me(
    current_user: Annotated[AuthenticatedUser, Depends(get_current_user)],
) -> AuthSessionResponse:
    return AuthSessionResponse(
        user={
            "id": current_user.id,
            "email": current_user.email,
            "email_confirmed": True,
        },
        access_token=current_user.access_token,
    )
