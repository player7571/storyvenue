from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_message_service
from app.api.schemas.message import MessageResponse
from app.services.message_service import (
    MessagePersistenceError,
    MessageService,
    MessageSessionNotFoundError,
)

router = APIRouter(tags=["messages"])


@router.get(
    "/messages/{session_id}",
    response_model=list[MessageResponse],
    summary="List session messages",
)
def list_messages(
    session_id: UUID,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    message_service: Annotated[MessageService, Depends(get_message_service)],
) -> list[MessageResponse]:
    try:
        return message_service.list_messages(user_id=user_id, session_id=session_id)
    except MessageSessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found.",
        ) from exc
    except MessagePersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to fetch messages.",
        ) from exc
