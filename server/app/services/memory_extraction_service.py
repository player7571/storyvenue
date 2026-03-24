from pydantic import BaseModel, ConfigDict, Field

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.core.config import get_settings


DEFAULT_MEMORY_EXTRACTION_PROMPT = """
너는 자서전 작성 보조 시스템의 정보 추출 모듈이다.
사용자 답변에서 자서전 작성에 필요한 기억 요소를 구조화해서 추출한다.

반드시 아래 항목만 추출하라.
- period
- place
- person
- event
- emotions
- meaning

규칙:
- 추측하지 말고 답변에 근거가 있을 때만 채운다.
- 없는 값은 null 로 둔다.
- emotions 는 배열로 반환한다.
- 결과는 한국어로 작성한다.
- JSON 외의 텍스트는 출력하지 않는다.
""".strip()


class MemoryExtractionServiceError(Exception):
    """Raised when memory extraction fails."""


class MemoryExtractionConfigurationError(MemoryExtractionServiceError):
    """Raised when memory extraction configuration is missing."""


class MemoryExtractionInputError(MemoryExtractionServiceError):
    """Raised when the input text is invalid."""


class ExtractedMemoryItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    period: str | None = None
    place: str | None = None
    person: str | None = None
    event: str | None = None
    emotions: list[str] | None = None
    meaning: str | None = None

    def normalized(self) -> "ExtractedMemoryItem":
        return ExtractedMemoryItem(
            period=_clean_optional_text(self.period),
            place=_clean_optional_text(self.place),
            person=_clean_optional_text(self.person),
            event=_clean_optional_text(self.event),
            emotions=_clean_emotions(self.emotions),
            meaning=_clean_optional_text(self.meaning),
        )

    def has_structured_value(self) -> bool:
        return any(
            [
                self.period,
                self.place,
                self.person,
                self.event,
                self.meaning,
                self.emotions,
            ]
        )


class ExtractedMemoryItems(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[ExtractedMemoryItem] = Field(default_factory=list)


class MemoryExtractionResult(BaseModel):
    items: list[ExtractedMemoryItem] = Field(default_factory=list)
    model: str | None = None


class MemoryExtractionService:
    def extract(self, *, text: str) -> MemoryExtractionResult:
        raise NotImplementedError


class OpenAIMemoryExtractionService(MemoryExtractionService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        system_prompt: str | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise MemoryExtractionConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_memory_model
        self.system_prompt = (
            system_prompt
            if system_prompt is not None
            else settings.openai_memory_prompt or DEFAULT_MEMORY_EXTRACTION_PROMPT
        )

    def extract(self, *, text: str) -> MemoryExtractionResult:
        cleaned_text = text.strip()
        if not cleaned_text:
            raise MemoryExtractionInputError("Source text is empty.")

        try:
            parsed = self._parse_structured_output(cleaned_text)
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
        ) as exc:
            raise MemoryExtractionServiceError("OpenAI memory extraction request failed.") from exc
        except Exception as exc:  # pragma: no cover - defensive fallback
            raise MemoryExtractionServiceError("Unexpected memory extraction failure.") from exc

        items = []
        for item in parsed.items:
            normalized_item = item.normalized()
            if normalized_item.has_structured_value():
                items.append(normalized_item)

        return MemoryExtractionResult(
            items=items,
            model=self.model,
        )

    def _parse_structured_output(self, cleaned_text: str) -> ExtractedMemoryItems:
        user_prompt = (
            "다음 사용자 답변에서 자서전용 memory item 을 추출해 주세요.\n\n"
            f"사용자 답변:\n{cleaned_text}"
        )

        responses_api = getattr(self.client, "responses", None)
        parse_response = getattr(responses_api, "parse", None)
        if callable(parse_response):
            response = parse_response(
                model=self.model,
                instructions=self.system_prompt,
                input=user_prompt,
                text_format=ExtractedMemoryItems,
            )
            parsed = getattr(response, "output_parsed", None)
            if parsed is None:
                raise MemoryExtractionServiceError(
                    "OpenAI did not return a structured memory extraction result."
                )
            return parsed

        completion = self.client.beta.chat.completions.parse(
            model=self.model,
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            response_format=ExtractedMemoryItems,
        )
        choice = completion.choices[0]
        message = choice.message
        refusal = getattr(message, "refusal", None)
        if refusal:
            raise MemoryExtractionServiceError("OpenAI refused memory extraction.")

        parsed = getattr(message, "parsed", None)
        if parsed is None:
            raise MemoryExtractionServiceError(
                "OpenAI did not return a structured memory extraction result."
            )

        return parsed


def _clean_optional_text(value: str | None) -> str | None:
    if value is None:
        return None

    normalized = value.strip()
    return normalized or None


def _clean_emotions(values: list[str] | None) -> list[str] | None:
    if not values:
        return None

    normalized = [value.strip() for value in values if value and value.strip()]
    return normalized or None
