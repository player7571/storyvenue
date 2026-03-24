from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_voice_turn_service
from app.api.schemas.voice import VoiceTurnResponse
from app.services.stt_service import UploadedAudio
from app.services.voice_turn_service import (
    VoiceTurnInputError,
    VoiceTurnProcessingError,
    VoiceTurnService,
    VoiceTurnSessionNotFoundError,
)

router = APIRouter(tags=["voice"])


@router.post(
    "/voice/turn",
    response_model=VoiceTurnResponse,
    summary="Process voice turn",
)
async def process_voice_turn(
    session_id: Annotated[UUID, Form()],
    audio_file: Annotated[UploadFile, File()],
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    voice_turn_service: Annotated[VoiceTurnService, Depends(get_voice_turn_service)],
    mime_type: Annotated[str | None, Form()] = None,
    language_hint: Annotated[str | None, Form()] = None,
) -> VoiceTurnResponse:
    try:
        audio_bytes = await audio_file.read()
    finally:
        await audio_file.close()

    uploaded_audio = UploadedAudio(
        filename=audio_file.filename or "audio",
        content_type=audio_file.content_type,
        mime_type=mime_type,
        language_hint=language_hint,
        data=audio_bytes,
    )

    try:
        return voice_turn_service.process_turn(
            user_id=user_id,
            session_id=session_id,
            audio=uploaded_audio,
        )
    except VoiceTurnInputError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded audio file is empty.",
        ) from exc
    except VoiceTurnSessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found.",
        ) from exc
    except VoiceTurnProcessingError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to process voice turn.",
        ) from exc
