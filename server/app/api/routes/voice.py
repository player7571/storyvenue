from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from app.api.dependencies.auth import get_current_user_id
from app.api.dependencies.services import get_voice_turn_service
from app.api.schemas.voice import (
    VoiceRepeatLastRequest,
    VoiceRepeatLastResponse,
    VoiceTurnResponse,
)
from app.services.stt_service import UploadedAudio
from app.services.voice_turn_service import (
    VoiceTurnAssistantMessageNotFoundError,
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


@router.post(
    "/voice/repeat-last",
    response_model=VoiceRepeatLastResponse,
    summary="Repeat last assistant voice response",
)
def repeat_last_voice(
    request: VoiceRepeatLastRequest,
    user_id: Annotated[UUID, Depends(get_current_user_id)],
    voice_turn_service: Annotated[VoiceTurnService, Depends(get_voice_turn_service)],
) -> VoiceRepeatLastResponse:
    try:
        assistant_message, audio_reply_url = voice_turn_service.repeat_last_assistant(
            user_id=user_id,
            session_id=request.session_id,
        )
    except VoiceTurnSessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found.",
        ) from exc
    except VoiceTurnAssistantMessageNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No assistant message found for the session.",
        ) from exc
    except VoiceTurnProcessingError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to repeat last assistant voice.",
        ) from exc

    return VoiceRepeatLastResponse(
        assistant_message_id=assistant_message.id,
        content=assistant_message.content,
        audio_reply_url=audio_reply_url,
    )
