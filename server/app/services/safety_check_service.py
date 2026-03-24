from typing import Literal

from pydantic import BaseModel, ConfigDict

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.core.config import get_settings


DEFAULT_SAFETY_CHECK_PROMPT = """
너는 음성 인터뷰 앱의 안전 확인 모듈이다.
입력된 사용자 발화에 응급상황 또는 고위험 신호가 있는지 판단한다.

출력 필드:
- safety_mode: boolean
- severity: low | medium | high
- reason: string
- recommended_action: string

규칙:
- 의료 진단을 하지 않는다.
- 고위험 신호가 있으면 보수적으로 판단한다.
- 숨이 차다, 가슴이 아프다, 쓰러졌다, 일어날 수 없다, 너무 아프다,
  죽고 싶다, 사라지고 싶다, 사는 의미가 없다, 큰일 났다, 도와달라,
  혼자 있는데 위험하다는 표현은 고위험 신호로 본다.
- JSON 외의 텍스트는 출력하지 않는다.
""".strip()

DEFAULT_SAFE_REASON = "명확한 고위험 신호가 확인되지 않았습니다."
DEFAULT_HIGH_RISK_REASON = "응급 또는 고위험 신호가 감지되었습니다."
DEFAULT_HIGH_RISK_ACTION = (
    "지금은 인터뷰보다 안전이 더 중요합니다. "
    "응급상황처럼 느껴지면 바로 119에 도움을 요청해 주세요. "
    "주변에 계신 분이나 보호자에게도 바로 도움을 요청해 보세요."
)
HIGH_RISK_KEYWORDS = (
    "숨이 차",
    "가슴이 아프",
    "쓰러졌",
    "일어날 수 없",
    "너무 아프",
    "죽고 싶",
    "사라지고 싶",
    "사는 의미가 없",
    "큰일 났",
    "도와달라",
    "혼자 있는데 위험",
)


class SafetyCheckServiceError(Exception):
    """Raised when safety checking fails."""


class SafetyCheckConfigurationError(SafetyCheckServiceError):
    """Raised when safety check configuration is missing."""


class SafetyCheckInputError(SafetyCheckServiceError):
    """Raised when the input text is invalid."""


class ParsedSafetyCheck(BaseModel):
    model_config = ConfigDict(extra="forbid")

    safety_mode: bool = False
    severity: Literal["low", "medium", "high"] = "low"
    reason: str = ""
    recommended_action: str | None = None

    def normalized(self) -> "ParsedSafetyCheck":
        normalized_reason = _clean_optional_text(self.reason)
        normalized_action = _clean_optional_text(self.recommended_action)
        normalized_safety_mode = bool(self.safety_mode or self.severity == "high")

        if normalized_reason is None:
            normalized_reason = (
                DEFAULT_HIGH_RISK_REASON if normalized_safety_mode else DEFAULT_SAFE_REASON
            )
        if normalized_safety_mode and normalized_action is None:
            normalized_action = DEFAULT_HIGH_RISK_ACTION

        return ParsedSafetyCheck(
            safety_mode=normalized_safety_mode,
            severity=self.severity,
            reason=normalized_reason,
            recommended_action=normalized_action,
        )


class SafetyCheckResult(BaseModel):
    safety_mode: bool = False
    severity: Literal["low", "medium", "high"] = "low"
    reason: str = DEFAULT_SAFE_REASON
    recommended_action: str | None = None
    model: str | None = None

    def assistant_guidance(self) -> str:
        if not self.safety_mode:
            raise SafetyCheckInputError("assistant_guidance is only available in safety mode.")

        normalized_action = _clean_optional_text(self.recommended_action)
        if not normalized_action:
            return DEFAULT_HIGH_RISK_ACTION

        if normalized_action.startswith("지금은 인터뷰보다 안전이 더 중요합니다."):
            return normalized_action

        return f"지금은 인터뷰보다 안전이 더 중요합니다. {normalized_action}"


class SafetyCheckService:
    def check_text(self, *, text: str) -> SafetyCheckResult:
        raise NotImplementedError


class KeywordSafetyCheckService(SafetyCheckService):
    def __init__(self, keywords: tuple[str, ...] = HIGH_RISK_KEYWORDS) -> None:
        self.keywords = keywords

    def check_text(self, *, text: str) -> SafetyCheckResult:
        cleaned_text = text.strip()
        if not cleaned_text:
            raise SafetyCheckInputError("Safety source text is empty.")

        matches = [keyword for keyword in self.keywords if keyword in cleaned_text]
        if matches:
            reason = f"고위험 키워드 감지: {', '.join(matches[:2])}"
            return SafetyCheckResult(
                safety_mode=True,
                severity="high",
                reason=reason,
                recommended_action=DEFAULT_HIGH_RISK_ACTION,
                model="keyword-fallback",
            )

        return SafetyCheckResult(
            safety_mode=False,
            severity="low",
            reason=DEFAULT_SAFE_REASON,
            recommended_action=None,
            model="keyword-fallback",
        )


class OpenAISafetyCheckService(SafetyCheckService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        system_prompt: str | None = None,
        fallback_service: SafetyCheckService | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise SafetyCheckConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_safety_model
        self.system_prompt = (
            system_prompt
            if system_prompt is not None
            else settings.openai_safety_prompt or DEFAULT_SAFETY_CHECK_PROMPT
        )
        self.fallback_service = fallback_service or KeywordSafetyCheckService()

    def check_text(self, *, text: str) -> SafetyCheckResult:
        cleaned_text = text.strip()
        if not cleaned_text:
            raise SafetyCheckInputError("Safety source text is empty.")

        try:
            parsed = self._parse_structured_output(cleaned_text).normalized()
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
            SafetyCheckServiceError,
        ):
            return self.fallback_service.check_text(text=cleaned_text)
        except Exception:
            return self.fallback_service.check_text(text=cleaned_text)

        return SafetyCheckResult(
            safety_mode=parsed.safety_mode,
            severity=parsed.severity,
            reason=parsed.reason,
            recommended_action=parsed.recommended_action,
            model=self.model,
        )

    def _parse_structured_output(self, cleaned_text: str) -> ParsedSafetyCheck:
        user_prompt = (
            "다음 사용자 발화를 보고 안전 우선 대응이 필요한지 판단해 주세요.\n\n"
            f"사용자 발화:\n{cleaned_text}"
        )

        responses_api = getattr(self.client, "responses", None)
        parse_response = getattr(responses_api, "parse", None)
        if callable(parse_response):
            response = parse_response(
                model=self.model,
                instructions=self.system_prompt,
                input=user_prompt,
                text_format=ParsedSafetyCheck,
            )
            parsed = getattr(response, "output_parsed", None)
            if parsed is None:
                raise SafetyCheckServiceError(
                    "OpenAI did not return a structured safety check result."
                )
            return parsed

        completion = self.client.beta.chat.completions.parse(
            model=self.model,
            messages=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            response_format=ParsedSafetyCheck,
        )
        choice = completion.choices[0]
        message = choice.message
        refusal = getattr(message, "refusal", None)
        if refusal:
            raise SafetyCheckServiceError("OpenAI refused safety checking.")

        parsed = getattr(message, "parsed", None)
        if parsed is None:
            raise SafetyCheckServiceError(
                "OpenAI did not return a structured safety check result."
            )

        return parsed


def _clean_optional_text(value: str | None) -> str | None:
    if value is None:
        return None

    normalized = value.strip()
    return normalized or None
