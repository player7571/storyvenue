from pydantic import BaseModel, ConfigDict, Field

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.core.config import get_settings


DEFAULT_FEED_ANALYSIS_PROMPT = """
너는 자서전 커뮤니티 피드 분석 모듈이다.
입력된 자서전 본문이나 관심 검색어를 읽고, 공감 추천에 쓸 신호를 구조화해서 추출한다.

반드시 아래 필드만 반환한다.
- summary
- topics
- emotions
- experiences

규칙:
- summary 는 1~2문장으로 짧고 명확하게 작성한다.
- topics 는 가족, 직업, 상실, 성취, 건강, 이주, 결혼, 자녀 같은 주제 중심으로 최대 6개.
- emotions 는 기쁨, 그리움, 외로움, 후회, 감사, 자부심 같은 감정 중심으로 최대 6개.
- experiences 는 은퇴, 전쟁 경험, 사업, 육아, 간병, 이민, 직업 전환, 배우자 상실 같은 삶의 경험 중심으로 최대 6개.
- 없는 내용은 추측하지 않는다.
- 결과는 한국어로 작성한다.
- JSON 외의 텍스트는 출력하지 않는다.
""".strip()


class FeedAnalysisServiceError(Exception):
    """Raised when feed analysis fails."""


class FeedAnalysisConfigurationError(FeedAnalysisServiceError):
    """Raised when feed analysis configuration is missing."""


class FeedAnalysisInputError(FeedAnalysisServiceError):
    """Raised when the source text is invalid."""


class ParsedFeedSignals(BaseModel):
    model_config = ConfigDict(extra="forbid")

    summary: str = Field(min_length=1, max_length=240)
    topics: list[str] = Field(default_factory=list)
    emotions: list[str] = Field(default_factory=list)
    experiences: list[str] = Field(default_factory=list)

    def normalized(self) -> "ParsedFeedSignals":
        return ParsedFeedSignals(
            summary=self.summary.strip(),
            topics=_normalize_items(self.topics),
            emotions=_normalize_items(self.emotions),
            experiences=_normalize_items(self.experiences),
        )


class FeedSignalsResult(BaseModel):
    summary: str
    topics: list[str] = Field(default_factory=list)
    emotions: list[str] = Field(default_factory=list)
    experiences: list[str] = Field(default_factory=list)
    model: str | None = None


class FeedAnalysisService:
    def analyze(self, *, text: str) -> FeedSignalsResult:
        raise NotImplementedError


class OpenAIFeedAnalysisService(FeedAnalysisService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        system_prompt: str | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise FeedAnalysisConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_feed_model
        self.system_prompt = (
            system_prompt
            if system_prompt is not None
            else settings.openai_feed_prompt or DEFAULT_FEED_ANALYSIS_PROMPT
        )

    def analyze(self, *, text: str) -> FeedSignalsResult:
        cleaned_text = text.strip()
        if not cleaned_text:
            raise FeedAnalysisInputError("Feed analysis source text is empty.")

        try:
            parsed = self._parse(cleaned_text).normalized()
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
        ) as exc:
            raise FeedAnalysisServiceError("OpenAI feed analysis request failed.") from exc
        except Exception as exc:  # pragma: no cover - defensive fallback
            raise FeedAnalysisServiceError("Unexpected feed analysis failure.") from exc

        return FeedSignalsResult(
            summary=parsed.summary,
            topics=parsed.topics,
            emotions=parsed.emotions,
            experiences=parsed.experiences,
            model=self.model,
        )

    def _parse(self, cleaned_text: str) -> ParsedFeedSignals:
        user_prompt = (
            "다음 자서전 내용 또는 관심 검색어를 읽고 추천용 신호를 추출해 주세요.\n\n"
            f"{cleaned_text}"
        )

        responses_api = getattr(self.client, "responses", None)
        parse_response = getattr(responses_api, "parse", None)
        if callable(parse_response):
            response = parse_response(
                model=self.model,
                instructions=self.system_prompt,
                input=user_prompt,
                text_format=ParsedFeedSignals,
            )
            parsed = getattr(response, "output_parsed", None)
            if parsed is None:
                raise FeedAnalysisServiceError(
                    "OpenAI did not return structured feed signals."
                )
            return parsed

        completion = self.client.beta.chat.completions.parse(
            model=self.model,
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            response_format=ParsedFeedSignals,
        )
        choice = completion.choices[0]
        message = choice.message
        refusal = getattr(message, "refusal", None)
        if refusal:
            raise FeedAnalysisServiceError("OpenAI refused feed analysis.")

        parsed = getattr(message, "parsed", None)
        if parsed is None:
            raise FeedAnalysisServiceError("OpenAI did not return structured feed signals.")

        return parsed


def _normalize_items(values: list[str]) -> list[str]:
    normalized: list[str] = []
    for value in values:
        candidate = value.strip()
        if not candidate:
            continue
        if candidate in normalized:
            continue
        normalized.append(candidate)
        if len(normalized) >= 6:
            break
    return normalized
