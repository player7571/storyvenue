from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_memory_service
from app.api.schemas.memory import MemoryExtractRequest, MemoryExtractResponse
from app.services.memory_service import (
    MemoryInputError,
    MemoryMessageNotFoundError,
    MemoryPersistenceError,
    MemoryService,
    MemorySessionNotFoundError,
)

router = APIRouter(tags=["memory"])


@router.post(
    "/memory/extract",
    response_model=MemoryExtractResponse,
    summary="Extract and store memory items",
)
def extract_memory_items(
    request: MemoryExtractRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    memory_service: Annotated[MemoryService, Depends(get_memory_service)],
) -> MemoryExtractResponse:
    try:
        return memory_service.extract_and_store(user_id=user_id, request=request)
    except MemoryInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except MemorySessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found.",
        ) from exc
    except MemoryMessageNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Message not found.",
        ) from exc
    except MemoryPersistenceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to extract memory items.",
        ) from exc
