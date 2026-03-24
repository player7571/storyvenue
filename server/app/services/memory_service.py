from uuid import UUID, uuid4

from supabase import Client

from app.api.schemas.memory import (
    MemoryExtractRequest,
    MemoryExtractResponse,
    MemoryItemResponse,
)
from app.db.supabase import get_supabase_service_role_client
from app.services.memory_extraction_service import (
    ExtractedMemoryItem,
    MemoryExtractionConfigurationError,
    MemoryExtractionService,
    MemoryExtractionServiceError,
    OpenAIMemoryExtractionService,
)
from app.services.session_service import (
    SessionNotFoundError,
    SessionPersistenceError,
    SessionService,
)


class MemoryServiceError(Exception):
    """Base error for memory item extraction and persistence."""


class MemoryConfigurationError(MemoryServiceError):
    """Raised when the memory service is not configured."""


class MemoryInputError(MemoryServiceError):
    """Raised when the memory extraction request is invalid."""


class MemoryPersistenceError(MemoryServiceError):
    """Raised when memory items cannot be stored or fetched."""


class MemorySessionNotFoundError(MemoryServiceError):
    """Raised when the requested session does not exist for the user."""


class MemoryMessageNotFoundError(MemoryServiceError):
    """Raised when the requested message does not exist for the user."""


class MemoryService:
    def __init__(
        self,
        client: Client | None = None,
        extraction_service: MemoryExtractionService | None = None,
    ) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise MemoryConfigurationError(str(exc)) from exc

        self.session_service = SessionService(client=self.client)
        try:
            self.extraction_service = extraction_service or OpenAIMemoryExtractionService()
        except MemoryExtractionConfigurationError as exc:
            raise MemoryConfigurationError(str(exc)) from exc

    def extract_and_store(
        self,
        *,
        user_id: UUID,
        request: MemoryExtractRequest,
    ) -> MemoryExtractResponse:
        self._ensure_session_exists(user_id=user_id, session_id=request.session_id)
        source_text, source_message_id = self._resolve_source(
            user_id=user_id,
            request=request,
        )

        fallback_used = False
        try:
            extracted_items = self.extraction_service.extract(text=source_text).items
        except MemoryExtractionServiceError:
            fallback_used = True
            extracted_items = [ExtractedMemoryItem()]

        if not extracted_items and not fallback_used:
            return MemoryExtractResponse(items=[], fallback_used=False)

        saved_items = self._store_memory_items(
            user_id=user_id,
            session_id=request.session_id,
            message_id=source_message_id,
            raw_text=source_text,
            items=extracted_items,
        )

        return MemoryExtractResponse(
            items=saved_items,
            fallback_used=fallback_used,
        )

    def _ensure_session_exists(self, *, user_id: UUID, session_id: UUID) -> None:
        try:
            self.session_service.get_session(user_id=user_id, session_id=session_id)
        except SessionNotFoundError as exc:
            raise MemorySessionNotFoundError(str(session_id)) from exc
        except SessionPersistenceError as exc:
            raise MemoryPersistenceError(str(exc)) from exc

    def _resolve_source(
        self,
        *,
        user_id: UUID,
        request: MemoryExtractRequest,
    ) -> tuple[str, UUID | None]:
        if request.text is not None:
            return request.text, None

        if request.message_id is None:
            raise MemoryInputError("message_id or text is required.")

        try:
            response = (
                self.client.table("messages")
                .select("id, content")
                .eq("id", str(request.message_id))
                .eq("session_id", str(request.session_id))
                .eq("user_id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise MemoryPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise MemoryMessageNotFoundError(str(request.message_id))

        content = (rows[0].get("content") or "").strip()
        if not content:
            raise MemoryInputError("Message content is empty.")

        return content, request.message_id

    def _store_memory_items(
        self,
        *,
        user_id: UUID,
        session_id: UUID,
        message_id: UUID | None,
        raw_text: str,
        items: list[ExtractedMemoryItem],
    ) -> list[MemoryItemResponse]:
        memory_ids = [uuid4() for _ in items]
        payloads = []
        for memory_id, item in zip(memory_ids, items, strict=True):
            payload = {
                "id": str(memory_id),
                "session_id": str(session_id),
                "user_id": str(user_id),
                "message_id": str(message_id) if message_id else None,
                "period": item.period,
                "place": item.place,
                "person": item.person,
                "event": item.event,
                "emotions": item.emotions,
                "meaning": item.meaning,
                "raw_text": raw_text,
            }
            payloads.append(payload)

        try:
            self.client.table("memory_items").insert(payloads).execute()
            response = (
                self.client.table("memory_items")
                .select(
                    "id, session_id, message_id, period, place, person, event, "
                    "emotions, meaning, raw_text, created_at"
                )
                .in_("id", [str(memory_id) for memory_id in memory_ids])
                .eq("session_id", str(session_id))
                .eq("user_id", str(user_id))
                .order("created_at")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise MemoryPersistenceError(str(exc)) from exc

        rows = response.data or []
        if len(rows) != len(memory_ids):
            raise MemoryPersistenceError("Created memory items could not be reloaded.")

        return [MemoryItemResponse.model_validate(row) for row in rows]
