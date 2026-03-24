from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi import Query

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_chapter_service
from app.api.schemas.chapter import (
    ChapterGenerateRequest,
    ChapterGenerateResponse,
    ChapterUpdateRequest,
)
from app.services.chapter_service import (
    ChapterInputError,
    ChapterNotFoundError,
    ChapterPersistenceError,
    ChapterService,
    ChapterSessionNotFoundError,
    ChapterSourceNotFoundError,
)

router = APIRouter(tags=["chapters"])


@router.get(
    "/chapters",
    response_model=list[ChapterGenerateResponse],
    summary="List chapter drafts",
)
def list_chapters(
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chapter_service: Annotated[ChapterService, Depends(get_chapter_service)],
    session_id: UUID | None = Query(default=None),
) -> list[ChapterGenerateResponse]:
    try:
        return chapter_service.list_chapters(user_id=user_id, session_id=session_id)
    except ChapterPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to list chapter drafts.",
        ) from exc


@router.post(
    "/chapters/generate",
    response_model=ChapterGenerateResponse,
    summary="Generate chapter draft",
)
def generate_chapter(
    request: ChapterGenerateRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chapter_service: Annotated[ChapterService, Depends(get_chapter_service)],
) -> ChapterGenerateResponse:
    try:
        return chapter_service.generate_and_store(user_id=user_id, request=request)
    except ChapterInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except ChapterSessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found.",
        ) from exc
    except ChapterSourceNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(exc),
        ) from exc
    except ChapterPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to generate chapter draft.",
        ) from exc


@router.patch(
    "/chapters/{chapter_id}",
    response_model=ChapterGenerateResponse,
    summary="Revise or regenerate chapter draft",
)
def update_chapter(
    chapter_id: UUID,
    request: ChapterUpdateRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chapter_service: Annotated[ChapterService, Depends(get_chapter_service)],
) -> ChapterGenerateResponse:
    try:
        return chapter_service.update_existing(
            user_id=user_id,
            chapter_id=chapter_id,
            request=request,
        )
    except ChapterInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except ChapterNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Chapter not found.",
        ) from exc
    except ChapterSourceNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(exc),
        ) from exc
    except ChapterPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to update chapter draft.",
        ) from exc


@router.get(
    "/chapters/{chapter_id}",
    response_model=ChapterGenerateResponse,
    summary="Get chapter draft",
)
def get_chapter(
    chapter_id: UUID,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    chapter_service: Annotated[ChapterService, Depends(get_chapter_service)],
) -> ChapterGenerateResponse:
    try:
        return chapter_service.get_chapter(user_id=user_id, chapter_id=chapter_id)
    except ChapterNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Chapter not found.",
        ) from exc
    except ChapterPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to fetch chapter draft.",
        ) from exc
