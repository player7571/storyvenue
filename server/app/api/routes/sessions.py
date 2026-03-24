from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_session_service
from app.api.schemas.session import SessionCreateRequest, SessionResponse
from app.services.session_service import (
    SessionNotFoundError,
    SessionPersistenceError,
    SessionService,
)

router = APIRouter(tags=["sessions"])


@router.get(
    "/sessions",
    response_model=list[SessionResponse],
    summary="List sessions",
)
def list_sessions(
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    session_service: Annotated[SessionService, Depends(get_session_service)],
) -> list[SessionResponse]:
    try:
        return session_service.list_sessions(user_id=user_id)
    except SessionPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to list sessions.",
        ) from exc


@router.post(
    "/sessions",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create session",
)
def create_session(
    request: SessionCreateRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    session_service: Annotated[SessionService, Depends(get_session_service)],
) -> SessionResponse:
    try:
        return session_service.create_session(user_id=user_id, request=request)
    except SessionPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create session.",
        ) from exc


@router.get(
    "/sessions/{session_id}",
    response_model=SessionResponse,
    summary="Get session",
)
def get_session(
    session_id: UUID,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    session_service: Annotated[SessionService, Depends(get_session_service)],
) -> SessionResponse:
    try:
        return session_service.get_session(user_id=user_id, session_id=session_id)
    except SessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found.",
        ) from exc
    except SessionPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to fetch session.",
        ) from exc
