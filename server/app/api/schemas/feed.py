from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class FeedPublishRequest(BaseModel):
    book_id: UUID
    title_override: str | None = Field(default=None, max_length=120)

    model_config = ConfigDict(str_strip_whitespace=True)

    @field_validator("title_override")
    @classmethod
    def normalize_title_override(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class FeedReadEventRequest(BaseModel):
    dwell_seconds: int = Field(default=0, ge=0, le=36000)
    completed: bool = False
    query_text: str | None = Field(default=None, max_length=200)

    model_config = ConfigDict(str_strip_whitespace=True)

    @field_validator("query_text")
    @classmethod
    def normalize_query_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class FeedCommentCreateRequest(BaseModel):
    content: str = Field(min_length=1, max_length=1000)

    model_config = ConfigDict(str_strip_whitespace=True)


class FeedCommentResponse(BaseModel):
    id: UUID
    post_id: UUID
    user_id: UUID
    author_name: str
    content: str
    created_at: datetime | None = None


class FeedPostResponse(BaseModel):
    post_id: UUID
    book_id: UUID
    user_id: UUID
    author_name: str
    title: str
    excerpt: str
    content: str
    summary: str | None = None
    topics: list[str] = Field(default_factory=list)
    emotions: list[str] = Field(default_factory=list)
    experiences: list[str] = Field(default_factory=list)
    score: float | None = None
    created_at: datetime | None = None


class FeedPersonResponse(BaseModel):
    user_id: UUID
    author_name: str
    shared_topics: list[str] = Field(default_factory=list)
    shared_experiences: list[str] = Field(default_factory=list)
    score: float
