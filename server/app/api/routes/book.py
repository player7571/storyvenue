from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_book_service
from app.api.schemas.book import BookCompileRequest, BookVersionResponse
from app.services.book_service import (
    BookInputError,
    BookNotFoundError,
    BookPersistenceError,
    BookService,
    BookSessionNotFoundError,
    BookSourceNotFoundError,
)

router = APIRouter(tags=["book"])


@router.post(
    "/book/compile",
    response_model=BookVersionResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Compile chapter drafts into a saved book version",
)
def compile_book(
    request: BookCompileRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    book_service: Annotated[BookService, Depends(get_book_service)],
) -> BookVersionResponse:
    try:
        return book_service.compile_and_store(user_id=user_id, request=request)
    except BookInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except BookSessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found.",
        ) from exc
    except BookSourceNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(exc),
        ) from exc
    except BookPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to compile book version.",
        ) from exc


@router.get(
    "/book/versions",
    response_model=list[BookVersionResponse],
    summary="List saved book versions",
)
def list_book_versions(
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    book_service: Annotated[BookService, Depends(get_book_service)],
) -> list[BookVersionResponse]:
    try:
        return book_service.list_versions(user_id=user_id)
    except BookPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to list book versions.",
        ) from exc


@router.get(
    "/book/versions/{book_id}",
    response_model=BookVersionResponse,
    summary="Get a saved book version",
)
def get_book_version(
    book_id: UUID,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    book_service: Annotated[BookService, Depends(get_book_service)],
) -> BookVersionResponse:
    try:
        return book_service.get_version(user_id=user_id, book_id=book_id)
    except BookNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Book version not found.",
        ) from exc
    except BookPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to fetch book version.",
        ) from exc
