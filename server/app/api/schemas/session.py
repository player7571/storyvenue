from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class SessionCreateRequest(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    theme: str | None = Field(default=None, max_length=60)

    model_config = ConfigDict(str_strip_whitespace=True)


class SessionResponse(BaseModel):
    id: UUID
    title: str
    theme: str | None = None
    status: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)
