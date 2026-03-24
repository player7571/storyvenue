from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class MessageResponse(BaseModel):
    id: UUID
    role: str
    content: str
    safety_mode: bool = False
    created_at: datetime | None = None
