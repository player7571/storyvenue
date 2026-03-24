from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator


class BookCompileRequest(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    session_id: UUID | None = None
    chapter_ids: list[UUID] | None = Field(default=None, min_length=1)

    model_config = ConfigDict(str_strip_whitespace=True)

    @model_validator(mode="after")
    def validate_source(self) -> "BookCompileRequest":
        has_session_id = self.session_id is not None
        has_chapter_ids = self.chapter_ids is not None

        if has_session_id == has_chapter_ids:
            raise ValueError("Provide exactly one of session_id or chapter_ids.")

        return self


class BookVersionResponse(BaseModel):
    book_id: UUID
    title: str
    content: str
    chapter_ids: list[UUID] = Field(default_factory=list)
    created_at: datetime | None = None
