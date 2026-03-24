from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field, field_validator, model_validator


class MemoryExtractRequest(BaseModel):
    session_id: UUID
    message_id: UUID | None = None
    text: str | None = None

    @field_validator("text")
    @classmethod
    def normalize_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        text = value.strip()
        return text or None

    @model_validator(mode="after")
    def validate_source(self) -> "MemoryExtractRequest":
        has_message_id = self.message_id is not None
        has_text = self.text is not None

        if has_message_id == has_text:
            raise ValueError("Provide exactly one of message_id or text.")

        return self


class MemoryItemResponse(BaseModel):
    id: UUID
    session_id: UUID
    message_id: UUID | None = None
    period: str | None = None
    place: str | None = None
    person: str | None = None
    event: str | None = None
    emotions: list[str] | None = None
    meaning: str | None = None
    raw_text: str | None = None
    created_at: datetime | None = None


class MemoryExtractResponse(BaseModel):
    items: list[MemoryItemResponse] = Field(default_factory=list)
    fallback_used: bool = False
