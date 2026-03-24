from uuid import UUID

from pydantic import BaseModel

from app.api.schemas.message import MessageResponse


class VoiceTurnResponse(BaseModel):
    user_message: MessageResponse
    assistant_message: MessageResponse
    transcript: str
    audio_reply_url: str | None = None
    memory_items_created: int = 0
    safety_mode: bool = False


class VoiceRepeatLastRequest(BaseModel):
    session_id: UUID


class VoiceRepeatLastResponse(BaseModel):
    assistant_message_id: UUID
    content: str
    audio_reply_url: str
