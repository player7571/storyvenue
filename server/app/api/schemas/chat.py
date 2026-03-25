from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class ChatRoomCreateRequest(BaseModel):
    other_user_id: UUID


class ChatMessageCreateRequest(BaseModel):
    content: str = Field(min_length=1, max_length=2000)

    model_config = ConfigDict(str_strip_whitespace=True)


class ChatRoomResponse(BaseModel):
    room_id: UUID
    other_user_id: UUID
    other_user_name: str
    last_message_preview: str | None = None
    created_at: datetime | None = None


class ChatMessageResponse(BaseModel):
    id: UUID
    room_id: UUID
    sender_id: UUID
    sender_name: str
    content: str
    created_at: datetime | None = None
