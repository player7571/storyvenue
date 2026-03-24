from pydantic import BaseModel, ConfigDict, Field

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.core.config import get_settings
from app.services.chapter_generation_service import GeneratedChapterDraft


DEFAULT_CHAPTER_REVISION_PROMPT = """
너는 자서전 편집 도우미다.
기존 초안과 사용자의 수정 지시를 반영해 새 버전을 작성한다.

규칙:
- 사용자의 수정 지시를 최우선으로 반영한다.
- 원래 의미를 훼손하지 않는다.
- 사실을 새로 만들지 않는다.
- 문체 변경, 분량 조절, 특정 내용 강조 또는 삭제를 자연스럽게 반영한다.
- 회고형 한국어 문체를 유지하되 과장하거나 지나치게 시적으로 쓰지 않는다.
- title 과 content 를 구조화된 형태로 반환한다.
""".strip()


class ChapterRevisionServiceError(Exception):
    """Raised when chapter revision fails."""


class ChapterRevisionConfigurationError(ChapterRevisionServiceError):
    """Raised when chapter revision configuration is missing."""


class ChapterRevisionInputError(ChapterRevisionServiceError):
    """Raised when chapter revision input is invalid."""


class ChapterRevisionResult(BaseModel):
    title: str
    content: str
    model: str | None = None


class ChapterRevisionService:
    def revise(
        self,
        *,
        chapter_type: str | None,
        current_title: str,
        current_content: str,
        instruction: str,
    ) -> ChapterRevisionResult:
        raise NotImplementedError


class OpenAIChapterRevisionService(ChapterRevisionService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        system_prompt: str | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise ChapterRevisionConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_chapter_model
        self.system_prompt = system_prompt or DEFAULT_CHAPTER_REVISION_PROMPT

    def revise(
        self,
        *,
        chapter_type: str | None,
        current_title: str,
        current_content: str,
        instruction: str,
    ) -> ChapterRevisionResult:
        title = current_title.strip()
        content = current_content.strip()
        normalized_instruction = instruction.strip()

        if not title or not content:
            raise ChapterRevisionInputError("Current chapter draft is empty.")
        if not normalized_instruction:
            raise ChapterRevisionInputError("instruction is empty.")

        try:
            parsed = self._parse_structured_output(
                chapter_type=chapter_type,
                current_title=title,
                current_content=content,
                instruction=normalized_instruction,
            )
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
        ) as exc:
            raise ChapterRevisionServiceError("OpenAI chapter revision request failed.") from exc
        except Exception as exc:  # pragma: no cover - defensive fallback
            raise ChapterRevisionServiceError("Unexpected chapter revision failure.") from exc

        normalized = parsed.normalized()
        return ChapterRevisionResult(
            title=normalized.title,
            content=normalized.content,
            model=self.model,
        )

    def _parse_structured_output(
        self,
        *,
        chapter_type: str | None,
        current_title: str,
        current_content: str,
        instruction: str,
    ) -> GeneratedChapterDraft:
        user_prompt = (
            "아래 기존 chapter draft 와 사용자 수정 지시를 반영해 새 버전을 작성해 주세요.\n"
            "입력에 없는 사실을 새로 만들면 안 됩니다.\n\n"
            f"chapter_type: {chapter_type or 'unspecified'}\n"
            f"current_title: {current_title}\n\n"
            f"current_content:\n{current_content}\n\n"
            f"instruction:\n{instruction}"
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
                raise ChapterRevisionServiceError(
                    "OpenAI did not return a structured chapter revision."
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
            raise ChapterRevisionServiceError("OpenAI refused chapter revision.")

        parsed = getattr(message, "parsed", None)
        if parsed is None:
            raise ChapterRevisionServiceError(
                "OpenAI did not return a structured chapter revision."
            )

        return parsed
