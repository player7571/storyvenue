from uuid import UUID

from supabase import Client

from app.api.schemas.chat import (
    ChatMessageCreateRequest,
    ChatMessageResponse,
    ChatRoomCreateRequest,
    ChatRoomResponse,
)
from app.db.supabase import get_supabase_service_role_client
from app.services.profile_service import ProfileService


class ChatServiceError(Exception):
    """Base error for chat features."""


class ChatConfigurationError(ChatServiceError):
    """Raised when the chat service is not configured."""


class ChatInputError(ChatServiceError):
    """Raised when chat input is invalid."""


class ChatPersistenceError(ChatServiceError):
    """Raised when chat data cannot be read or written."""


class ChatRoomNotFoundError(ChatServiceError):
    """Raised when the requested room does not exist."""


class ChatRoomAccessError(ChatServiceError):
    """Raised when the current user does not belong to the room."""


class ChatService:
    def __init__(self, client: Client | None = None) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise ChatConfigurationError(str(exc)) from exc

        self.profile_service = ProfileService(client=self.client)

    def list_rooms(
        self,
        *,
        user_id: UUID,
    ) -> list[ChatRoomResponse]:
        rows = self._load_room_rows(user_id=user_id)
        last_messages = self._load_last_message_preview_by_room(
            [str(row["id"]) for row in rows]
        )
        responses: list[ChatRoomResponse] = []
        for row in rows:
            other_user_id = self._other_user_id(row=row, user_id=user_id)
            responses.append(
                ChatRoomResponse(
                    room_id=UUID(str(row["id"])),
                    other_user_id=other_user_id,
                    other_user_name=self.profile_service.get_display_name(
                        user_id=other_user_id
                    ),
                    last_message_preview=last_messages.get(str(row["id"])),
                    created_at=row.get("created_at"),
                )
            )
        return responses

    def create_room(
        self,
        *,
        user_id: UUID,
        request: ChatRoomCreateRequest,
    ) -> ChatRoomResponse:
        other_user_id = request.other_user_id
        if other_user_id == user_id:
            raise ChatInputError("자기 자신과는 채팅방을 만들 수 없습니다.")

        room_key, member_a_id, member_b_id = self._build_room_key(
            user_id=user_id,
            other_user_id=other_user_id,
        )
        existing = self._load_room_by_key(room_key=room_key)
        if existing is not None:
            return self._build_room_response(row=existing, user_id=user_id)

        payload = {
            "room_key": room_key,
            "member_a_id": str(member_a_id),
            "member_b_id": str(member_b_id),
        }
        try:
            response = self.client.table("chat_rooms").insert(payload).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChatPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise ChatPersistenceError("채팅방을 만든 뒤 다시 불러오지 못했습니다.")

        return self._build_room_response(row=rows[0], user_id=user_id)

    def list_messages(
        self,
        *,
        user_id: UUID,
        room_id: UUID,
    ) -> list[ChatMessageResponse]:
        self._ensure_room_member(user_id=user_id, room_id=room_id)

        try:
            response = (
                self.client.table("chat_messages")
                .select("id, room_id, sender_id, sender_name, content, created_at")
                .eq("room_id", str(room_id))
                .order("created_at")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChatPersistenceError(str(exc)) from exc

        return [
            ChatMessageResponse.model_validate(row)
            for row in (response.data or [])
        ]

    def create_message(
        self,
        *,
        user_id: UUID,
        room_id: UUID,
        request: ChatMessageCreateRequest,
    ) -> ChatMessageResponse:
        self._ensure_room_member(user_id=user_id, room_id=room_id)
        sender_name = self.profile_service.get_display_name(user_id=user_id)
        payload = {
            "room_id": str(room_id),
            "sender_id": str(user_id),
            "sender_name": sender_name,
            "content": request.content,
        }

        try:
            response = self.client.table("chat_messages").insert(payload).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChatPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise ChatPersistenceError("메시지를 저장했지만 다시 불러오지 못했습니다.")

        return ChatMessageResponse.model_validate(rows[0])

    def _load_room_rows(self, *, user_id: UUID) -> list[dict]:
        try:
            response = (
                self.client.table("chat_rooms")
                .select("id, member_a_id, member_b_id, created_at")
                .or_(
                    f"member_a_id.eq.{user_id},member_b_id.eq.{user_id}"
                )
                .order("created_at", desc=True)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChatPersistenceError(str(exc)) from exc

        return response.data or []

    def _load_last_message_preview_by_room(
        self,
        room_ids: list[str],
    ) -> dict[str, str]:
        if not room_ids:
            return {}

        try:
            response = (
                self.client.table("chat_messages")
                .select("room_id, content, created_at")
                .in_("room_id", room_ids)
                .order("created_at", desc=True)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChatPersistenceError(str(exc)) from exc

        previews: dict[str, str] = {}
        for row in response.data or []:
            room_id = str(row["room_id"])
            if room_id in previews:
                continue
            previews[room_id] = (row.get("content") or "").strip()[:100]
        return previews

    def _load_room_by_key(self, *, room_key: str) -> dict | None:
        try:
            response = (
                self.client.table("chat_rooms")
                .select("id, member_a_id, member_b_id, created_at")
                .eq("room_key", room_key)
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChatPersistenceError(str(exc)) from exc

        rows = response.data or []
        return rows[0] if rows else None

    def _build_room_response(
        self,
        *,
        row: dict,
        user_id: UUID,
    ) -> ChatRoomResponse:
        other_user_id = self._other_user_id(row=row, user_id=user_id)
        previews = self._load_last_message_preview_by_room([str(row["id"])])
        return ChatRoomResponse(
            room_id=UUID(str(row["id"])),
            other_user_id=other_user_id,
            other_user_name=self.profile_service.get_display_name(user_id=other_user_id),
            last_message_preview=previews.get(str(row["id"])),
            created_at=row.get("created_at"),
        )

    def _ensure_room_member(
        self,
        *,
        user_id: UUID,
        room_id: UUID,
    ) -> dict:
        try:
            response = (
                self.client.table("chat_rooms")
                .select("id, member_a_id, member_b_id, created_at")
                .eq("id", str(room_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise ChatPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise ChatRoomNotFoundError(str(room_id))

        row = rows[0]
        members = {str(row["member_a_id"]), str(row["member_b_id"])}
        if str(user_id) not in members:
            raise ChatRoomAccessError(str(room_id))
        return row

    def _other_user_id(
        self,
        *,
        row: dict,
        user_id: UUID,
    ) -> UUID:
        member_a_id = UUID(str(row["member_a_id"]))
        member_b_id = UUID(str(row["member_b_id"]))
        if member_a_id == user_id:
            return member_b_id
        return member_a_id

    def _build_room_key(
        self,
        *,
        user_id: UUID,
        other_user_id: UUID,
    ) -> tuple[str, UUID, UUID]:
        ordered = sorted([str(user_id), str(other_user_id)])
        member_a_id = UUID(ordered[0])
        member_b_id = UUID(ordered[1])
        return f"{ordered[0]}:{ordered[1]}", member_a_id, member_b_id
