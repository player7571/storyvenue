from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID, uuid4

from supabase import Client

from app.api.schemas.chapter import (
    ChapterGenerateRequest,
    ChapterGenerateResponse,
    ChapterUpdateRequest,
)
from app.api.schemas.memory import MemoryItemResponse
from app.db.supabase import get_supabase_service_role_client
from app.services.chapter_generation_service import (
    ChapterGenerationConfigurationError,
    ChapterGenerationService,
    ChapterGenerationServiceError,
    OpenAIChapterGenerationService,
)
from app.services.chapter_revision_service import (
    ChapterRevisionConfigurationError,
    ChapterRevisionService,
    ChapterRevisionServiceError,
    OpenAIChapterRevisionService,
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


class ChapterNotFoundError(ChapterServiceError):
    """Raised when the requested chapter draft does not exist for the user."""


class ChapterSourceNotFoundError(ChapterServiceError):
    """Raised when chapter source memory items are missing."""


@dataclass(frozen=True)
class StoredChapterDraft:
    id: UUID
    session_id: UUID | None
    chapter_type: str | None
    title: str
    content: str
    version_no: int
    created_at: datetime | None = None


class ChapterService:
    def __init__(
        self,
        client: Client | None = None,
        generation_service: ChapterGenerationService | None = None,
        revision_service: ChapterRevisionService | None = None,
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
        try:
            self.revision_service = revision_service or OpenAIChapterRevisionService()
        except ChapterRevisionConfigurationError as exc:
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

        return self._create_chapter_draft(
            user_id=user_id,
            session_id=resolved_session_id,
            chapter_type=request.chapter_type,
            title=generated.title,
            content=generated.content,
        )

    def update_existing(
        self,
        *,
        user_id: UUID,
        chapter_id: UUID,
        request: ChapterUpdateRequest,
    ) -> ChapterGenerateResponse:
        existing = self._load_chapter_draft(user_id=user_id, chapter_id=chapter_id)

        if request.regenerate:
            if existing.session_id is None:
                raise ChapterInputError(
                    "Regeneration is only supported for chapter drafts with session_id."
                )
            if not existing.chapter_type:
                raise ChapterInputError(
                    "Regeneration requires the existing chapter_type to be present."
                )

            memory_items, _ = self._load_memory_items_for_session(
                user_id=user_id,
                session_id=existing.session_id,
            )
            try:
                updated = self.generation_service.generate(
                    chapter_type=existing.chapter_type,
                    memory_items=memory_items,
                )
            except ChapterGenerationServiceError as exc:
                raise ChapterPersistenceError(str(exc)) from exc
        else:
            if request.instruction is None:
                raise ChapterInputError("instruction is required.")
            try:
                updated = self.revision_service.revise(
                    chapter_type=existing.chapter_type,
                    current_title=existing.title,
                    current_content=existing.content,
                    instruction=request.instruction,
                )
            except ChapterRevisionServiceError as exc:
                raise ChapterPersistenceError(str(exc)) from exc

        next_version_no = existing.version_no + 1
        updated_at = datetime.now(timezone.utc).isoformat()
        payload = {
            "title": updated.title,
            "content": updated.content,
            "version_no": next_version_no,
            "updated_at": updated_at,
        }

        try:
            self.client.table("chapter_drafts").update(payload).eq("id", str(chapter_id)).eq(
                "user_id", str(user_id)
            ).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChapterPersistenceError(str(exc)) from exc

        refreshed = self._load_chapter_draft(user_id=user_id, chapter_id=chapter_id)
        return ChapterGenerateResponse(
            chapter_id=refreshed.id,
            session_id=refreshed.session_id,
            chapter_type=refreshed.chapter_type,
            title=refreshed.title,
            content=refreshed.content,
            version_no=refreshed.version_no,
            created_at=refreshed.created_at,
        )

    def list_chapters(
        self,
        *,
        user_id: UUID,
        session_id: UUID | None = None,
    ) -> list[ChapterGenerateResponse]:
        try:
            query = (
                self.client.table("chapter_drafts")
                .select("id, session_id, chapter_type, title, content, version_no, created_at")
                .eq("user_id", str(user_id))
            )
            if session_id is not None:
                query = query.eq("session_id", str(session_id))
            response = query.order("created_at").execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChapterPersistenceError(str(exc)) from exc

        rows = response.data or []
        return [
            ChapterGenerateResponse(
                chapter_id=UUID(str(row["id"])),
                session_id=UUID(str(row["session_id"])) if row.get("session_id") else None,
                chapter_type=row.get("chapter_type"),
                title=row["title"],
                content=row["content"],
                version_no=int(row.get("version_no", 1)),
                created_at=row.get("created_at"),
            )
            for row in rows
        ]

    def get_chapter(
        self,
        *,
        user_id: UUID,
        chapter_id: UUID,
    ) -> ChapterGenerateResponse:
        stored = self._load_chapter_draft(user_id=user_id, chapter_id=chapter_id)
        return ChapterGenerateResponse(
            chapter_id=stored.id,
            session_id=stored.session_id,
            chapter_type=stored.chapter_type,
            title=stored.title,
            content=stored.content,
            version_no=stored.version_no,
            created_at=stored.created_at,
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

    def _create_chapter_draft(
        self,
        *,
        user_id: UUID,
        session_id: UUID | None,
        chapter_type: str,
        title: str,
        content: str,
    ) -> ChapterGenerateResponse:
        chapter_id = uuid4()
        payload = {
            "id": str(chapter_id),
            "user_id": str(user_id),
            "session_id": str(session_id) if session_id else None,
            "chapter_type": chapter_type,
            "title": title,
            "content": content,
        }

        try:
            self.client.table("chapter_drafts").insert(payload).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChapterPersistenceError(str(exc)) from exc

        stored = self._load_chapter_draft(user_id=user_id, chapter_id=chapter_id)
        return ChapterGenerateResponse(
            chapter_id=stored.id,
            session_id=stored.session_id,
            chapter_type=stored.chapter_type,
            title=stored.title,
            content=stored.content,
            version_no=stored.version_no,
            created_at=stored.created_at,
        )

    def _load_chapter_draft(
        self,
        *,
        user_id: UUID,
        chapter_id: UUID,
    ) -> StoredChapterDraft:
        try:
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
            raise ChapterNotFoundError(str(chapter_id))

        row = rows[0]
        return StoredChapterDraft(
            id=UUID(str(row["id"])),
            session_id=UUID(str(row["session_id"])) if row.get("session_id") else None,
            chapter_type=row.get("chapter_type"),
            title=row["title"],
            content=row["content"],
            version_no=int(row.get("version_no", 1)),
            created_at=row.get("created_at"),
        )
