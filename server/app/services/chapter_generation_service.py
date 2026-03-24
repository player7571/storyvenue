import json

from pydantic import BaseModel, ConfigDict, Field

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.api.schemas.memory import MemoryItemResponse
from app.core.config import get_settings


DEFAULT_CHAPTER_GENERATION_PROMPT = """
너는 자서전 초안 작성 도우미다.
입력된 memory item 을 바탕으로 장별 자서전 초안을 작성한다.

목표:
- 사실을 왜곡하지 않고
- 과장하지 않으며
- 자연스럽고 읽기 쉬운 회고형 문장으로 작성한다.

규칙:
- 입력 정보에 없는 사실을 추가하지 않는다.
- 너무 시적이거나 과장된 문체를 피한다.
- 담백하지만 따뜻한 문체를 사용한다.
- chapter title 1개와 3~5개 문단 분량의 본문을 작성한다.
- 한국어로 작성한다.
""".strip()


class ChapterGenerationServiceError(Exception):
    """Raised when chapter generation fails."""


class ChapterGenerationConfigurationError(ChapterGenerationServiceError):
    """Raised when chapter generation configuration is missing."""


class ChapterGenerationInputError(ChapterGenerationServiceError):
    """Raised when chapter generation input is invalid."""


class GeneratedChapterDraft(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=120)
    content: str = Field(min_length=1)

    def normalized(self) -> "GeneratedChapterDraft":
        title = self.title.strip()
        content = self.content.strip()
        if not title or not content:
            raise ChapterGenerationInputError("Generated chapter draft was empty.")

        return GeneratedChapterDraft(
            title=title,
            content=content,
        )


class ChapterGenerationResult(BaseModel):
    title: str
    content: str
    model: str | None = None


class ChapterGenerationService:
    def generate(
        self,
        *,
        chapter_type: str,
        memory_items: list[MemoryItemResponse],
    ) -> ChapterGenerationResult:
        raise NotImplementedError


class OpenAIChapterGenerationService(ChapterGenerationService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        system_prompt: str | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise ChapterGenerationConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_chapter_model
        self.system_prompt = (
            system_prompt
            if system_prompt is not None
            else settings.openai_chapter_prompt or DEFAULT_CHAPTER_GENERATION_PROMPT
        )

    def generate(
        self,
        *,
        chapter_type: str,
        memory_items: list[MemoryItemResponse],
    ) -> ChapterGenerationResult:
        normalized_chapter_type = chapter_type.strip()
        if not normalized_chapter_type:
            raise ChapterGenerationInputError("chapter_type is empty.")
        if not memory_items:
            raise ChapterGenerationInputError("memory_items are empty.")

        try:
            parsed = self._parse_structured_output(
                chapter_type=normalized_chapter_type,
                memory_items=memory_items,
            )
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
        ) as exc:
            raise ChapterGenerationServiceError("OpenAI chapter generation request failed.") from exc
        except Exception as exc:  # pragma: no cover - defensive fallback
            raise ChapterGenerationServiceError("Unexpected chapter generation failure.") from exc

        normalized = parsed.normalized()
        return ChapterGenerationResult(
            title=normalized.title,
            content=normalized.content,
            model=self.model,
        )

    def _parse_structured_output(
        self,
        *,
        chapter_type: str,
        memory_items: list[MemoryItemResponse],
    ) -> GeneratedChapterDraft:
        prompt_payload = {
            "chapter_type": chapter_type,
            "memory_items": [
                {
                    "period": item.period,
                    "place": item.place,
                    "person": item.person,
                    "event": item.event,
                    "emotions": item.emotions,
                    "meaning": item.meaning,
                    "raw_text": item.raw_text,
                }
                for item in memory_items
            ],
        }

        user_prompt = (
            "아래 memory item 묶음을 바탕으로 장 초안을 작성해 주세요.\n"
            "chapter_type 는 장의 방향을 위한 힌트이며, memory item 에 없는 사실을 새로 만들면 안 됩니다.\n"
            "title 과 content 를 구조화된 형태로 반환해 주세요.\n\n"
            f"{json.dumps(prompt_payload, ensure_ascii=False, indent=2)}"
        )

        responses_api = getattr(self.client, "responses", None)
        parse_response = getattr(responses_api, "parse", None)
        if callable(parse_response):
            response = parse_response(
                model=self.model,
                instructions=self.system_prompt,
                input=user_prompt,
                text_format=GeneratedChapterDraft,
            )
            parsed = getattr(response, "output_parsed", None)
            if parsed is None:
                raise ChapterGenerationServiceError(
                    "OpenAI did not return a structured chapter draft."
                )
            return parsed

        completion = self.client.beta.chat.completions.parse(
            model=self.model,
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            response_format=GeneratedChapterDraft,
        )
        choice = completion.choices[0]
        message = choice.message
        refusal = getattr(message, "refusal", None)
        if refusal:
            raise ChapterGenerationServiceError("OpenAI refused chapter generation.")

        parsed = getattr(message, "parsed", None)
        if parsed is None:
            raise ChapterGenerationServiceError(
                "OpenAI did not return a structured chapter draft."
            )

        return parsed
