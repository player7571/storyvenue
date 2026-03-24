from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies.services import get_safety_check_service
from app.api.schemas.safety import SafetyCheckRequest, SafetyCheckResponse
from app.services.safety_check_service import (
    SafetyCheckInputError,
    SafetyCheckService,
    SafetyCheckServiceError,
)

router = APIRouter(tags=["safety"])


@router.post(
    "/safety/check",
    response_model=SafetyCheckResponse,
    summary="Check high-risk safety signals",
)
def check_safety(
    request: SafetyCheckRequest,
    safety_check_service: Annotated[SafetyCheckService, Depends(get_safety_check_service)],
) -> SafetyCheckResponse:
    try:
        result = safety_check_service.check_text(text=request.text)
    except SafetyCheckInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except SafetyCheckServiceError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to check safety signals.",
        ) from exc

    return SafetyCheckResponse(
        safety_mode=result.safety_mode,
        severity=result.severity,
        reason=result.reason,
        recommended_action=result.recommended_action,
    )
