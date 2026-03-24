from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ChapterGenerateRequest(BaseModel):
    chapter_type: str = Field(min_length=1, max_length=60)
    session_id: UUID | None = None
    memory_item_ids: list[UUID] | None = Field(default=None, min_length=1)

    model_config = ConfigDict(str_strip_whitespace=True)

    @model_validator(mode="after")
    def validate_source(self) -> "ChapterGenerateRequest":
        has_session_id = self.session_id is not None
        has_memory_item_ids = self.memory_item_ids is not None

        if has_session_id == has_memory_item_ids:
            raise ValueError("Provide exactly one of session_id or memory_item_ids.")

        return self


class ChapterGenerateResponse(BaseModel):
    chapter_id: UUID
    session_id: UUID | None = None
    chapter_type: str | None = None
    title: str
    content: str
    version_no: int = 1
    created_at: datetime | None = None
