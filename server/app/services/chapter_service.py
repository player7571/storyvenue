from uuid import UUID, uuid4

from supabase import Client

from app.api.schemas.chapter import ChapterGenerateRequest, ChapterGenerateResponse
from app.api.schemas.memory import MemoryItemResponse
from app.db.supabase import get_supabase_service_role_client
from app.services.chapter_generation_service import (
    ChapterGenerationConfigurationError,
    ChapterGenerationService,
    ChapterGenerationServiceError,
    OpenAIChapterGenerationService,
)
from app.services.session_service import (
    SessionNotFoundError,
    SessionPersistenceError,
    SessionService,
)


class ChapterServiceError(Exception):
    """Base error for chapter generation and persistence."""


class ChapterConfigurationError(ChapterServiceError):
    """Raised when the chapter service is not configured."""


class ChapterInputError(ChapterServiceError):
    """Raised when the chapter generation request is invalid."""


class ChapterPersistenceError(ChapterServiceError):
    """Raised when chapter drafts cannot be stored or fetched."""


class ChapterSessionNotFoundError(ChapterServiceError):
    """Raised when the requested session does not exist for the user."""


class ChapterSourceNotFoundError(ChapterServiceError):
    """Raised when chapter source memory items are missing."""


class ChapterService:
    def __init__(
        self,
        client: Client | None = None,
        generation_service: ChapterGenerationService | None = None,
    ) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise ChapterConfigurationError(str(exc)) from exc

        self.session_service = SessionService(client=self.client)
        try:
            self.generation_service = generation_service or OpenAIChapterGenerationService()
        except ChapterGenerationConfigurationError as exc:
            raise ChapterConfigurationError(str(exc)) from exc

    def generate_and_store(
        self,
        *,
        user_id: UUID,
        request: ChapterGenerateRequest,
    ) -> ChapterGenerateResponse:
        memory_items, resolved_session_id = self._resolve_memory_items(
            user_id=user_id,
            request=request,
        )

        try:
            generated = self.generation_service.generate(
                chapter_type=request.chapter_type,
                memory_items=memory_items,
            )
        except ChapterGenerationServiceError as exc:
            raise ChapterPersistenceError(str(exc)) from exc

        chapter_id = uuid4()
        payload = {
            "id": str(chapter_id),
            "user_id": str(user_id),
            "session_id": str(resolved_session_id) if resolved_session_id else None,
            "chapter_type": request.chapter_type,
            "title": generated.title,
            "content": generated.content,
        }

        try:
            self.client.table("chapter_drafts").insert(payload).execute()
            response = (
                self.client.table("chapter_drafts")
                .select("id, session_id, chapter_type, title, content, version_no, created_at")
                .eq("id", str(chapter_id))
                .eq("user_id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChapterPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise ChapterPersistenceError("Created chapter draft could not be reloaded.")

        row = rows[0]
        return ChapterGenerateResponse(
            chapter_id=row["id"],
            session_id=row.get("session_id"),
            chapter_type=row.get("chapter_type"),
            title=row["title"],
            content=row["content"],
            version_no=row.get("version_no", 1),
            created_at=row.get("created_at"),
        )

    def _resolve_memory_items(
        self,
        *,
        user_id: UUID,
        request: ChapterGenerateRequest,
    ) -> tuple[list[MemoryItemResponse], UUID | None]:
        if request.session_id is not None:
            return self._load_memory_items_for_session(
                user_id=user_id,
                session_id=request.session_id,
            )

        if request.memory_item_ids is None:
            raise ChapterInputError("session_id or memory_item_ids is required.")

        return self._load_memory_items_by_ids(
            user_id=user_id,
            memory_item_ids=request.memory_item_ids,
        )

    def _load_memory_items_for_session(
        self,
        *,
        user_id: UUID,
        session_id: UUID,
    ) -> tuple[list[MemoryItemResponse], UUID]:
        try:
            self.session_service.get_session(user_id=user_id, session_id=session_id)
        except SessionNotFoundError as exc:
            raise ChapterSessionNotFoundError(str(session_id)) from exc
        except SessionPersistenceError as exc:
            raise ChapterPersistenceError(str(exc)) from exc

        try:
            response = (
                self.client.table("memory_items")
                .select(
                    "id, session_id, message_id, period, place, person, event, "
                    "emotions, meaning, raw_text, created_at"
                )
                .eq("session_id", str(session_id))
                .eq("user_id", str(user_id))
                .order("created_at")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChapterPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise ChapterSourceNotFoundError("No memory items found for the session.")

        items = [MemoryItemResponse.model_validate(row) for row in rows]
        return items, session_id

    def _load_memory_items_by_ids(
        self,
        *,
        user_id: UUID,
        memory_item_ids: list[UUID],
    ) -> tuple[list[MemoryItemResponse], UUID | None]:
        try:
            response = (
                self.client.table("memory_items")
                .select(
                    "id, session_id, message_id, period, place, person, event, "
                    "emotions, meaning, raw_text, created_at"
                )
                .in_("id", [str(memory_item_id) for memory_item_id in memory_item_ids])
                .eq("user_id", str(user_id))
                .order("created_at")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChapterPersistenceError(str(exc)) from exc

        rows = response.data or []
        if len(rows) != len(memory_item_ids):
            raise ChapterSourceNotFoundError("One or more memory items were not found.")

        items = [MemoryItemResponse.model_validate(row) for row in rows]
        unique_session_ids = {
            item.session_id
            for item in items
            if item.session_id is not None
        }
        resolved_session_id = unique_session_ids.pop() if len(unique_session_ids) == 1 else None
        return items, resolved_session_id
