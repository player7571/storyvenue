from app.services.book_service import BookConfigurationError, BookService
from app.services.chat_service import ChatConfigurationError, ChatService
from app.services.feed_service import FeedConfigurationError, FeedService
from fastapi import HTTPException, status

from app.services.chapter_service import ChapterConfigurationError, ChapterService
from app.services.memory_service import MemoryConfigurationError, MemoryService
from app.services.message_service import MessageConfigurationError, MessageService
from app.services.safety_check_service import (
    OpenAISafetyCheckService,
    SafetyCheckConfigurationError,
    SafetyCheckService,
)
from app.services.session_service import SessionConfigurationError, SessionService
from app.services.voice_turn_service import (
    VoiceTurnConfigurationError,
    VoiceTurnService,
)


def get_session_service() -> SessionService:
    try:
        return SessionService()
    except SessionConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Supabase session service is not configured.",
        ) from exc


def get_message_service() -> MessageService:
    try:
        return MessageService()
    except MessageConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Supabase message service is not configured.",
        ) from exc


def get_safety_check_service() -> SafetyCheckService:
    try:
        return OpenAISafetyCheckService()
    except SafetyCheckConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Safety check service is not configured.",
        ) from exc


def get_memory_service() -> MemoryService:
    try:
        return MemoryService()
    except MemoryConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Memory extraction service is not configured.",
        ) from exc


def get_chapter_service() -> ChapterService:
    try:
        return ChapterService()
    except ChapterConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Chapter generation service is not configured.",
        ) from exc


def get_book_service() -> BookService:
    try:
        return BookService()
    except BookConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Book service is not configured.",
        ) from exc


def get_feed_service() -> FeedService:
    try:
        return FeedService()
    except FeedConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Feed service is not configured.",
        ) from exc


def get_chat_service() -> ChatService:
    try:
        return ChatService()
    except ChatConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Chat service is not configured.",
        ) from exc


def get_voice_turn_service() -> VoiceTurnService:
    try:
        return VoiceTurnService()
    except VoiceTurnConfigurationError as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Supabase voice turn service is not configured.",
        ) from exc
