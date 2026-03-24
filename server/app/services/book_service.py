from uuid import UUID

from supabase import Client

from app.api.schemas.book import BookCompileRequest, BookVersionResponse
from app.db.supabase import get_supabase_service_role_client
from app.services.session_service import (
    SessionNotFoundError,
    SessionPersistenceError,
    SessionService,
)


class BookServiceError(Exception):
    """Base error for book compilation."""


class BookConfigurationError(BookServiceError):
    """Raised when book service is not configured."""


class BookInputError(BookServiceError):
    """Raised when the book compile input is invalid."""


class BookPersistenceError(BookServiceError):
    """Raised when book versions cannot be stored or fetched."""


class BookSessionNotFoundError(BookServiceError):
    """Raised when the requested session does not exist for the user."""


class BookSourceNotFoundError(BookServiceError):
    """Raised when no usable chapters exist for a book compile request."""


class BookNotFoundError(BookServiceError):
    """Raised when a book version cannot be found."""


class BookService:
    def __init__(self, client: Client | None = None) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise BookConfigurationError(str(exc)) from exc

        self.session_service = SessionService(client=self.client)

    def compile_and_store(
        self,
        *,
        user_id: UUID,
        request: BookCompileRequest,
    ) -> BookVersionResponse:
        chapter_rows = self._resolve_chapters(user_id=user_id, request=request)

        compiled_content = "\n\n".join(
            [
                f"{index}. {row['title']}\n\n{row['content'].strip()}"
                for index, row in enumerate(chapter_rows, start=1)
            ]
        ).strip()
        if not compiled_content:
            raise BookInputError("Compiled book content is empty.")

        payload = {
            "user_id": str(user_id),
            "title": request.title,
            "content": compiled_content,
            "chapter_ids": [row["id"] for row in chapter_rows],
        }

        try:
            response = (
                self.client.table("autobiography_versions")
                .insert(payload)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise BookPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise BookPersistenceError("Created book version could not be reloaded.")

        return BookVersionResponse.model_validate(
            {
                "book_id": rows[0]["id"],
                "title": rows[0]["title"],
                "content": rows[0]["content"],
                "chapter_ids": rows[0].get("chapter_ids") or [],
                "created_at": rows[0].get("created_at"),
            }
        )

    def list_versions(
        self,
        *,
        user_id: UUID,
    ) -> list[BookVersionResponse]:
        try:
            response = (
                self.client.table("autobiography_versions")
                .select("id, title, content, chapter_ids, created_at")
                .eq("user_id", str(user_id))
                .order("created_at", desc=True)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise BookPersistenceError(str(exc)) from exc

        rows = response.data or []
        return [
            BookVersionResponse.model_validate(
                {
                    "book_id": row["id"],
                    "title": row["title"],
                    "content": row["content"],
                    "chapter_ids": row.get("chapter_ids") or [],
                    "created_at": row.get("created_at"),
                }
            )
            for row in rows
        ]

    def get_version(
        self,
        *,
        user_id: UUID,
        book_id: UUID,
    ) -> BookVersionResponse:
        try:
            response = (
                self.client.table("autobiography_versions")
                .select("id, title, content, chapter_ids, created_at")
                .eq("id", str(book_id))
                .eq("user_id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise BookPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise BookNotFoundError(str(book_id))

        row = rows[0]
        return BookVersionResponse.model_validate(
            {
                "book_id": row["id"],
                "title": row["title"],
                "content": row["content"],
                "chapter_ids": row.get("chapter_ids") or [],
                "created_at": row.get("created_at"),
            }
        )

    def _resolve_chapters(
        self,
        *,
        user_id: UUID,
        request: BookCompileRequest,
    ) -> list[dict]:
        if request.chapter_ids is not None:
            return self._load_chapters_by_ids(
                user_id=user_id,
                chapter_ids=request.chapter_ids,
            )

        if request.session_id is None:
            raise BookInputError("session_id or chapter_ids is required.")

        try:
            self.session_service.get_session(user_id=user_id, session_id=request.session_id)
        except SessionNotFoundError as exc:
            raise BookSessionNotFoundError(str(request.session_id)) from exc
        except SessionPersistenceError as exc:
            raise BookPersistenceError(str(exc)) from exc

        return self._load_chapters_for_session(
            user_id=user_id,
            session_id=request.session_id,
        )

    def _load_chapters_for_session(
        self,
        *,
        user_id: UUID,
        session_id: UUID,
    ) -> list[dict]:
        try:
            response = (
                self.client.table("chapter_drafts")
                .select("id, title, content, created_at")
                .eq("user_id", str(user_id))
                .eq("session_id", str(session_id))
                .order("created_at")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise BookPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise BookSourceNotFoundError("No chapter drafts found for the session.")

        return rows

    def _load_chapters_by_ids(
        self,
        *,
        user_id: UUID,
        chapter_ids: list[UUID],
    ) -> list[dict]:
        string_ids = [str(chapter_id) for chapter_id in chapter_ids]
        try:
            response = (
                self.client.table("chapter_drafts")
                .select("id, title, content, created_at")
                .in_("id", string_ids)
                .eq("user_id", str(user_id))
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise BookPersistenceError(str(exc)) from exc

        row_by_id = {row["id"]: row for row in response.data or []}
        missing_ids = [chapter_id for chapter_id in string_ids if chapter_id not in row_by_id]
        if missing_ids:
            raise BookSourceNotFoundError("Some chapter drafts could not be found.")

        return [row_by_id[chapter_id] for chapter_id in string_ids]
