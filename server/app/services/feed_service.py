from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from supabase import Client

from app.api.schemas.feed import (
    FeedCommentCreateRequest,
    FeedCommentResponse,
    FeedPersonResponse,
    FeedPostResponse,
    FeedPublishRequest,
    FeedReadEventRequest,
)
from app.db.supabase import get_supabase_service_role_client
from app.services.feed_analysis_service import (
    FeedAnalysisConfigurationError,
    FeedAnalysisInputError,
    FeedAnalysisService,
    FeedAnalysisServiceError,
    FeedSignalsResult,
    OpenAIFeedAnalysisService,
)
from app.services.profile_service import ProfileService


class FeedServiceError(Exception):
    """Base error for feed features."""


class FeedConfigurationError(FeedServiceError):
    """Raised when the feed service is not configured."""


class FeedInputError(FeedServiceError):
    """Raised when feed input is invalid."""


class FeedPersistenceError(FeedServiceError):
    """Raised when feed data cannot be read or written."""


class FeedBookNotFoundError(FeedServiceError):
    """Raised when the source autobiography version does not exist."""


class FeedPostNotFoundError(FeedServiceError):
    """Raised when the feed post does not exist."""


@dataclass
class UserFeedProfile:
    topic_weights: dict[str, float]
    emotion_weights: dict[str, float]
    experience_weights: dict[str, float]

    @property
    def has_signals(self) -> bool:
        return bool(
            self.topic_weights or self.emotion_weights or self.experience_weights
        )


class FeedService:
    def __init__(
        self,
        client: Client | None = None,
        analysis_service: FeedAnalysisService | None = None,
    ) -> None:
        try:
            self.client = client or get_supabase_service_role_client()
        except RuntimeError as exc:
            raise FeedConfigurationError(str(exc)) from exc

        self.profile_service = ProfileService(client=self.client)
        if analysis_service is not None:
            self.analysis_service = analysis_service
        else:
            try:
                self.analysis_service = OpenAIFeedAnalysisService()
            except FeedAnalysisConfigurationError:
                self.analysis_service = None

    def publish_post(
        self,
        *,
        user_id: UUID,
        request: FeedPublishRequest,
    ) -> FeedPostResponse:
        book_row = self._load_book_row(user_id=user_id, book_id=request.book_id)
        author_name = self.profile_service.get_display_name(user_id=user_id)
        content = (book_row.get("content") or "").strip()
        if not content:
            raise FeedInputError("게시할 자서전 내용이 비어 있습니다.")

        signals = self._analyze_text(content)
        payload = {
            "book_id": str(request.book_id),
            "user_id": str(user_id),
            "author_name": author_name,
            "title": request.title_override or book_row["title"],
            "excerpt": self._build_excerpt(content),
            "content": content,
            "summary": signals.summary,
            "topics": signals.topics,
            "emotions": signals.emotions,
            "experiences": signals.experiences,
            "updated_at": datetime.utcnow().isoformat(),
        }

        try:
            response = (
                self.client.table("feed_posts")
                .upsert(payload, on_conflict="book_id")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise FeedPersistenceError("게시된 피드를 다시 불러오지 못했습니다.")

        return self._build_post_response(rows[0], score=None)

    def list_posts(
        self,
        *,
        user_id: UUID,
        query_text: str | None = None,
        limit: int = 30,
    ) -> list[FeedPostResponse]:
        feed_rows = self._load_feed_rows(limit=max(limit, 1))
        if not feed_rows:
            return []

        profile = self._build_user_profile(user_id=user_id, query_text=query_text)
        popularity_by_post_id = self._load_popularity_by_post_ids(
            [UUID(str(row["id"])) for row in feed_rows]
        )

        scored = [
            (
                self._score_post(
                    row=row,
                    viewer_user_id=user_id,
                    profile=profile,
                    popularity_score=popularity_by_post_id.get(str(row["id"]), 0.0),
                ),
                row,
            )
            for row in feed_rows
        ]
        scored.sort(
            key=lambda item: (
                item[0],
                item[1].get("created_at") or "",
            ),
            reverse=True,
        )
        return [
            self._build_post_response(row, score=score)
            for score, row in scored[:limit]
        ]

    def list_people(
        self,
        *,
        user_id: UUID,
        query_text: str | None = None,
        limit: int = 10,
    ) -> list[FeedPersonResponse]:
        feed_rows = self._load_feed_rows(limit=60)
        if not feed_rows:
            return []

        profile = self._build_user_profile(user_id=user_id, query_text=query_text)
        popularity_by_post_id = self._load_popularity_by_post_ids(
            [UUID(str(row["id"])) for row in feed_rows]
        )

        grouped: dict[str, dict] = {}
        for row in feed_rows:
            author_id = str(row["user_id"])
            if author_id == str(user_id):
                continue

            score = self._score_post(
                row=row,
                viewer_user_id=user_id,
                profile=profile,
                popularity_score=popularity_by_post_id.get(str(row["id"]), 0.0),
            )
            if score <= 0:
                continue

            existing = grouped.get(author_id)
            shared_topics = self._shared_items(
                profile.topic_weights,
                self._signal_list(row.get("topics")),
            )
            shared_experiences = self._shared_items(
                profile.experience_weights,
                self._signal_list(row.get("experiences")),
            )
            if existing is None:
                grouped[author_id] = {
                    "user_id": author_id,
                    "author_name": row.get("author_name") or "사용자",
                    "shared_topics": shared_topics,
                    "shared_experiences": shared_experiences,
                    "score": score,
                }
                continue

            existing["score"] = max(existing["score"], score)
            existing["shared_topics"] = self._merge_unique(
                existing["shared_topics"],
                shared_topics,
            )
            existing["shared_experiences"] = self._merge_unique(
                existing["shared_experiences"],
                shared_experiences,
            )

        ranked = sorted(grouped.values(), key=lambda item: item["score"], reverse=True)
        return [
            FeedPersonResponse.model_validate(person)
            for person in ranked[:limit]
        ]

    def get_post(
        self,
        *,
        post_id: UUID,
    ) -> FeedPostResponse:
        row = self._load_post_row(post_id=post_id)
        return self._build_post_response(row, score=None)

    def list_comments(
        self,
        *,
        post_id: UUID,
    ) -> list[FeedCommentResponse]:
        self._load_post_row(post_id=post_id)

        try:
            response = (
                self.client.table("feed_comments")
                .select("id, post_id, user_id, author_name, content, created_at")
                .eq("post_id", str(post_id))
                .order("created_at")
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        return [
            FeedCommentResponse.model_validate(row)
            for row in (response.data or [])
        ]

    def create_comment(
        self,
        *,
        user_id: UUID,
        post_id: UUID,
        request: FeedCommentCreateRequest,
    ) -> FeedCommentResponse:
        self._load_post_row(post_id=post_id)
        author_name = self.profile_service.get_display_name(user_id=user_id)
        payload = {
            "post_id": str(post_id),
            "user_id": str(user_id),
            "author_name": author_name,
            "content": request.content,
        }

        try:
            response = self.client.table("feed_comments").insert(payload).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise FeedPersistenceError("댓글을 저장했지만 다시 불러오지 못했습니다.")

        return FeedCommentResponse.model_validate(rows[0])

    def record_read_event(
        self,
        *,
        user_id: UUID,
        post_id: UUID,
        request: FeedReadEventRequest,
    ) -> None:
        self._load_post_row(post_id=post_id)

        payload = {
            "post_id": str(post_id),
            "user_id": str(user_id),
            "dwell_seconds": request.dwell_seconds,
            "completed": request.completed,
            "query_text": request.query_text,
        }

        try:
            self.client.table("feed_read_events").insert(payload).execute()
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

    def _load_book_row(
        self,
        *,
        user_id: UUID,
        book_id: UUID,
    ) -> dict:
        try:
            response = (
                self.client.table("autobiography_versions")
                .select("id, title, content")
                .eq("id", str(book_id))
                .eq("user_id", str(user_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise FeedBookNotFoundError(str(book_id))
        return rows[0]

    def _load_post_row(
        self,
        *,
        post_id: UUID,
    ) -> dict:
        try:
            response = (
                self.client.table("feed_posts")
                .select(
                    "id, book_id, user_id, author_name, title, excerpt, content, "
                    "summary, topics, emotions, experiences, created_at"
                )
                .eq("id", str(post_id))
                .limit(1)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        rows = response.data or []
        if not rows:
            raise FeedPostNotFoundError(str(post_id))
        return rows[0]

    def _load_feed_rows(self, *, limit: int) -> list[dict]:
        try:
            response = (
                self.client.table("feed_posts")
                .select(
                    "id, book_id, user_id, author_name, title, excerpt, content, "
                    "summary, topics, emotions, experiences, created_at"
                )
                .order("created_at", desc=True)
                .limit(limit)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        return response.data or []

    def _load_popularity_by_post_ids(
        self,
        post_ids: list[UUID],
    ) -> dict[str, float]:
        if not post_ids:
            return {}

        try:
            response = (
                self.client.table("feed_read_events")
                .select("post_id, dwell_seconds, completed")
                .in_("post_id", [str(post_id) for post_id in post_ids])
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        popularity: dict[str, float] = defaultdict(float)
        for row in response.data or []:
            post_id = str(row["post_id"])
            popularity[post_id] += 0.15
            popularity[post_id] += min(float(row.get("dwell_seconds") or 0) / 300.0, 2.0)
            if row.get("completed"):
                popularity[post_id] += 0.7
        return dict(popularity)

    def _build_user_profile(
        self,
        *,
        user_id: UUID,
        query_text: str | None,
    ) -> UserFeedProfile:
        topic_weights: defaultdict[str, float] = defaultdict(float)
        emotion_weights: defaultdict[str, float] = defaultdict(float)
        experience_weights: defaultdict[str, float] = defaultdict(float)

        own_posts = self._load_own_feed_posts(user_id=user_id)
        if own_posts:
            for row in own_posts[:2]:
                self._accumulate_from_row(
                    row=row,
                    topic_weights=topic_weights,
                    emotion_weights=emotion_weights,
                    experience_weights=experience_weights,
                    weight=2.0,
                )
        else:
            for row in self._load_own_books(user_id=user_id):
                signals = self._analyze_text((row.get("content") or "").strip())
                self._accumulate_from_signals(
                    signals=signals,
                    topic_weights=topic_weights,
                    emotion_weights=emotion_weights,
                    experience_weights=experience_weights,
                    weight=1.8,
                )

        read_events = self._load_recent_read_events(user_id=user_id)
        if read_events:
            post_ids = list(
                {
                    str(row["post_id"])
                    for row in read_events
                    if row.get("post_id")
                }
            )
            event_posts = self._load_posts_by_ids(post_ids)
            for event in read_events:
                post_row = event_posts.get(str(event.get("post_id")))
                if post_row is None:
                    continue
                dwell_seconds = int(event.get("dwell_seconds") or 0)
                weight = max(0.5, min(dwell_seconds / 45.0, 3.0))
                if event.get("completed"):
                    weight += 1.2
                self._accumulate_from_row(
                    row=post_row,
                    topic_weights=topic_weights,
                    emotion_weights=emotion_weights,
                    experience_weights=experience_weights,
                    weight=weight,
                )

        if query_text:
            query_signals = self._analyze_text(query_text)
            self._accumulate_from_signals(
                signals=query_signals,
                topic_weights=topic_weights,
                emotion_weights=emotion_weights,
                experience_weights=experience_weights,
                weight=3.0,
            )

        return UserFeedProfile(
            topic_weights=dict(topic_weights),
            emotion_weights=dict(emotion_weights),
            experience_weights=dict(experience_weights),
        )

    def _load_own_feed_posts(
        self,
        *,
        user_id: UUID,
    ) -> list[dict]:
        try:
            response = (
                self.client.table("feed_posts")
                .select("id, topics, emotions, experiences")
                .eq("user_id", str(user_id))
                .order("created_at", desc=True)
                .limit(2)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        return response.data or []

    def _load_own_books(
        self,
        *,
        user_id: UUID,
    ) -> list[dict]:
        try:
            response = (
                self.client.table("autobiography_versions")
                .select("id, content")
                .eq("user_id", str(user_id))
                .order("created_at", desc=True)
                .limit(2)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        return response.data or []

    def _load_recent_read_events(
        self,
        *,
        user_id: UUID,
    ) -> list[dict]:
        try:
            response = (
                self.client.table("feed_read_events")
                .select("post_id, dwell_seconds, completed")
                .eq("user_id", str(user_id))
                .order("created_at", desc=True)
                .limit(100)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        return response.data or []

    def _load_posts_by_ids(self, post_ids: list[str]) -> dict[str, dict]:
        if not post_ids:
            return {}

        try:
            response = (
                self.client.table("feed_posts")
                .select("id, topics, emotions, experiences")
                .in_("id", post_ids)
                .execute()
            )
        except Exception as exc:  # pragma: no cover - external client failure path
            raise FeedPersistenceError(str(exc)) from exc

        return {
            str(row["id"]): row
            for row in (response.data or [])
        }

    def _score_post(
        self,
        *,
        row: dict,
        viewer_user_id: UUID,
        profile: UserFeedProfile,
        popularity_score: float,
    ) -> float:
        topic_score = sum(
            profile.topic_weights.get(item, 0.0)
            for item in self._signal_list(row.get("topics"))
        )
        emotion_score = sum(
            profile.emotion_weights.get(item, 0.0)
            for item in self._signal_list(row.get("emotions"))
        )
        experience_score = sum(
            profile.experience_weights.get(item, 0.0)
            for item in self._signal_list(row.get("experiences"))
        )

        if not profile.has_signals:
            total = 1.0 + popularity_score
        else:
            total = (
                (topic_score * 1.2)
                + (emotion_score * 0.7)
                + (experience_score * 1.5)
                + (popularity_score * 0.25)
            )

        if str(row.get("user_id")) == str(viewer_user_id):
            total -= 0.75

        return round(total, 4)

    def _build_post_response(
        self,
        row: dict,
        *,
        score: float | None,
    ) -> FeedPostResponse:
        return FeedPostResponse.model_validate(
            {
                "post_id": row["id"],
                "book_id": row["book_id"],
                "user_id": row["user_id"],
                "author_name": row.get("author_name") or "사용자",
                "title": row.get("title") or "",
                "excerpt": row.get("excerpt") or "",
                "content": row.get("content") or "",
                "summary": row.get("summary"),
                "topics": self._signal_list(row.get("topics")),
                "emotions": self._signal_list(row.get("emotions")),
                "experiences": self._signal_list(row.get("experiences")),
                "score": score,
                "created_at": row.get("created_at"),
            }
        )

    def _accumulate_from_row(
        self,
        *,
        row: dict,
        topic_weights: defaultdict[str, float],
        emotion_weights: defaultdict[str, float],
        experience_weights: defaultdict[str, float],
        weight: float,
    ) -> None:
        for topic in self._signal_list(row.get("topics")):
            topic_weights[topic] += weight
        for emotion in self._signal_list(row.get("emotions")):
            emotion_weights[emotion] += weight
        for experience in self._signal_list(row.get("experiences")):
            experience_weights[experience] += weight

    def _accumulate_from_signals(
        self,
        *,
        signals: FeedSignalsResult,
        topic_weights: defaultdict[str, float],
        emotion_weights: defaultdict[str, float],
        experience_weights: defaultdict[str, float],
        weight: float,
    ) -> None:
        for topic in signals.topics:
            topic_weights[topic] += weight
        for emotion in signals.emotions:
            emotion_weights[emotion] += weight
        for experience in signals.experiences:
            experience_weights[experience] += weight

    def _analyze_text(self, text: str) -> FeedSignalsResult:
        cleaned_text = text.strip()
        if not cleaned_text:
            return FeedSignalsResult(summary="", topics=[], emotions=[], experiences=[])

        if self.analysis_service is None:
            return FeedSignalsResult(
                summary=self._build_excerpt(cleaned_text),
                topics=[],
                emotions=[],
                experiences=[],
            )

        try:
            return self.analysis_service.analyze(text=cleaned_text)
        except (FeedAnalysisInputError, FeedAnalysisServiceError):
            return FeedSignalsResult(
                summary=self._build_excerpt(cleaned_text),
                topics=[],
                emotions=[],
                experiences=[],
            )

    def _build_excerpt(self, text: str) -> str:
        cleaned = " ".join(text.split())
        return cleaned[:160].strip()

    def _signal_list(self, raw_value: object) -> list[str]:
        if not isinstance(raw_value, list):
            return []

        values: list[str] = []
        for item in raw_value:
            if not isinstance(item, str):
                continue
            cleaned = item.strip()
            if not cleaned or cleaned in values:
                continue
            values.append(cleaned)
        return values

    def _shared_items(
        self,
        weighted_items: dict[str, float],
        candidate_items: list[str],
    ) -> list[str]:
        shared = [item for item in candidate_items if weighted_items.get(item, 0.0) > 0]
        shared.sort(key=lambda item: weighted_items.get(item, 0.0), reverse=True)
        return shared[:3]

    def _merge_unique(self, current: list[str], incoming: list[str]) -> list[str]:
        merged = list(current)
        for item in incoming:
            if item not in merged:
                merged.append(item)
        return merged[:3]
