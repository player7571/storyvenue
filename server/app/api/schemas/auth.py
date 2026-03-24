from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class AuthCredentialsRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    password: str = Field(min_length=8, max_length=128)

    model_config = ConfigDict(str_strip_whitespace=True)

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: str) -> str:
        normalized = value.strip().lower()
        if "@" not in normalized:
            raise ValueError("email must be valid.")
        return normalized


class AuthRefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=1)

    model_config = ConfigDict(str_strip_whitespace=True)

    @field_validator("refresh_token")
    @classmethod
    def normalize_refresh_token(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("refresh_token is required.")
        return normalized

class AuthUserResponse(BaseModel):
    id: UUID
    email: str | None = None
    email_confirmed: bool = False


class AuthSessionResponse(BaseModel):
    user: AuthUserResponse
    access_token: str | None = None
    refresh_token: str | None = None
    token_type: str | None = None
    expires_at: int | None = None
    expires_in: int | None = None
    requires_email_confirmation: bool = False
    message: str | None = None
