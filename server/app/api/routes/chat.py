from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_chat_service
from app.api.schemas.chat import (
    ChatMessageCreateRequest,
    ChatMessageResponse,
    ChatRoomCreateRequest,
    ChatRoomResponse,
)
from app.services.chat_service import (
    ChatInputError,
    ChatPersistenceError,
    ChatRoomAccessError,
    ChatRoomNotFoundError,
    ChatService,
)

router = APIRouter(tags=["chat"])


@router.get(
    "/chat/rooms",
    response_model=list[ChatRoomResponse],
    summary="List chat rooms for the current user",
)
def list_chat_rooms(
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chat_service: Annotated[ChatService, Depends(get_chat_service)],
) -> list[ChatRoomResponse]:
    try:
        return chat_service.list_rooms(user_id=user_id)
    except ChatPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to load chat rooms.",
        ) from exc


@router.post(
    "/chat/rooms",
    response_model=ChatRoomResponse,
    summary="Create or reuse a 1:1 chat room",
)
def create_chat_room(
    request: ChatRoomCreateRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chat_service: Annotated[ChatService, Depends(get_chat_service)],
) -> ChatRoomResponse:
    try:
        return chat_service.create_room(user_id=user_id, request=request)
    except ChatInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except ChatPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create chat room.",
        ) from exc


@router.get(
    "/chat/rooms/{room_id}/messages",
    response_model=list[ChatMessageResponse],
    summary="List messages for a chat room",
)
def list_chat_messages(
    room_id: UUID,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chat_service: Annotated[ChatService, Depends(get_chat_service)],
) -> list[ChatMessageResponse]:
    try:
        return chat_service.list_messages(user_id=user_id, room_id=room_id)
    except ChatRoomNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Chat room not found.",
        ) from exc
    except ChatRoomAccessError as exc:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You do not have access to this chat room.",
        ) from exc
    except ChatPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to load chat messages.",
        ) from exc


@router.post(
    "/chat/rooms/{room_id}/messages",
    response_model=ChatMessageResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Send a chat message",
)
def create_chat_message(
    room_id: UUID,
    request: ChatMessageCreateRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chat_service: Annotated[ChatService, Depends(get_chat_service)],
) -> ChatMessageResponse:
    try:
        return chat_service.create_message(
            user_id=user_id,
            room_id=room_id,
            request=request,
        )
    except ChatRoomNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Chat room not found.",
        ) from exc
    except ChatRoomAccessError as exc:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You do not have access to this chat room.",
        ) from exc
    except ChatPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to send chat message.",
        ) from exc
