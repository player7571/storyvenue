from dataclasses import dataclass
from uuid import UUID

from supabase import Client
from supabase_auth.types import AuthResponse, User, UserResponse

from app.api.schemas.auth import AuthCredentialsRequest, AuthSessionResponse, AuthUserResponse
from app.db.supabase import create_supabase_anon_client


class AuthServiceError(Exception):
    """Base error for auth service."""


class AuthConfigurationError(AuthServiceError):
    """Raised when auth service is not configured."""


class AuthInvalidCredentialsError(AuthServiceError):
    """Raised when auth credentials are invalid."""


class AuthUnauthorizedError(AuthServiceError):
    """Raised when auth token validation fails."""


@dataclass(frozen=True)
class AuthenticatedUser:
    id: UUID
    email: str | None
    access_token: str | None = None


class AuthService:
    def __init__(self, client: Client | None = None) -> None:
        try:
            self.client = client or create_supabase_anon_client()
        except RuntimeError as exc:
            raise AuthConfigurationError(str(exc)) from exc

    def sign_up(self, request: AuthCredentialsRequest) -> AuthSessionResponse:
        try:
            response = self.client.auth.sign_up(
                {
                    "email": request.email,
                    "password": request.password,
                }
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise AuthInvalidCredentialsError(str(exc)) from exc

        return self._build_session_response(
            response,
            fallback_message=(
                "가입이 완료되었습니다. 이메일 확인이 켜져 있으면 메일을 확인한 뒤 로그인해 주세요."
            ),
        )

    def sign_in(self, request: AuthCredentialsRequest) -> AuthSessionResponse:
        try:
            response = self.client.auth.sign_in_with_password(
                {
                    "email": request.email,
                    "password": request.password,
                }
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise AuthInvalidCredentialsError(str(exc)) from exc

        if response.session is None or response.user is None:
            raise AuthInvalidCredentialsError("Sign-in did not return a valid session.")

        return self._build_session_response(response)

    def refresh(self, refresh_token: str) -> AuthSessionResponse:
        try:
            response = self.client.auth.refresh_session(refresh_token)
        except Exception as exc:  # pragma: no cover - external client failure path
            raise AuthUnauthorizedError(str(exc)) from exc

        if response.session is None or response.user is None:
            raise AuthUnauthorizedError("Refresh did not return a valid session.")

        return self._build_session_response(response)

    def get_user_from_access_token(self, access_token: str) -> AuthenticatedUser:
        if not access_token.strip():
            raise AuthUnauthorizedError("Access token is required.")

        try:
            response = self.client.auth.get_user(access_token)
        except Exception as exc:  # pragma: no cover - external client failure path
            raise AuthUnauthorizedError(str(exc)) from exc

        if response is None or response.user is None:
            raise AuthUnauthorizedError("Supabase did not return a user for the token.")

        user = response.user
        return AuthenticatedUser(
            id=UUID(user.id),
            email=user.email,
            access_token=access_token,
        )

    def _build_session_response(
        self,
        response: AuthResponse,
        fallback_message: str | None = None,
    ) -> AuthSessionResponse:
        user = response.user
        if user is None:
            raise AuthServiceError("Supabase auth response did not include a user.")

        session = response.session
        requires_email_confirmation = session is None

        return AuthSessionResponse(
            user=self._build_user_response(user),
            access_token=session.access_token if session else None,
            refresh_token=session.refresh_token if session else None,
            token_type=session.token_type if session else None,
            expires_at=session.expires_at if session else None,
            expires_in=session.expires_in if session else None,
            requires_email_confirmation=requires_email_confirmation,
            message=fallback_message if requires_email_confirmation else None,
        )

    def _build_user_response(self, user: User) -> AuthUserResponse:
        return AuthUserResponse(
            id=UUID(user.id),
            email=user.email,
            email_confirmed=bool(user.email_confirmed_at or user.confirmed_at),
        )
