from dataclasses import dataclass
from uuid import UUID

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI, RateLimitError

from app.core.config import get_settings


DEFAULT_TEXT_GENERATION_PROMPT = """
너는 노인 사용자의 삶의 이야기를 음성 대화로 편안하게 기록하도록 돕는 자서전 인터뷰 도우미다.

규칙:
- 짧고 쉬운 한국어 존댓말을 사용한다.
- 한 번에 질문은 하나만 한다.
- 사용자가 이미 말한 내용을 존중하고, 다음 질문은 하나만 이어간다.
- 잘못 들었을 수 있는 이름, 장소, 날짜, 관계 정보는 짧게 다시 확인할 수 있다.
- 너무 길게 설명하지 않는다.
- 사용자가 답하기 어려워하면 더 쉬운 질문으로 바꾼다.
- 민감한 내용을 강요하지 않는다.
- 출력은 assistant가 바로 말할 한두 문단의 짧은 한국어만 반환한다.
""".strip()


class TextGenerationServiceError(Exception):
    """Raised when assistant text generation fails."""


class TextGenerationConfigurationError(TextGenerationServiceError):
    """Raised when assistant text generation configuration is missing."""


@dataclass(frozen=True)
class ConversationTurn:
    role: str
    content: str


@dataclass(frozen=True)
class TextGenerationResult:
    text: str
    safety_mode: bool = False


class TextGenerationService:
    def generate_reply(
        self,
        *,
        session_id: UUID,
        transcript: str,
        conversation_history: list[ConversationTurn],
    ) -> TextGenerationResult:
        raise NotImplementedError


class OpenAITextGenerationService(TextGenerationService):
    def __init__(
        self,
        client: OpenAI | None = None,
        model: str | None = None,
        system_prompt: str | None = None,
    ) -> None:
        settings = get_settings()
        api_key = settings.openai_api_key

        if client is None and not api_key:
            raise TextGenerationConfigurationError("OPENAI_API_KEY is not configured.")

        self.client = client or OpenAI(api_key=api_key)
        self.model = model or settings.openai_text_model
        self.system_prompt = (
            system_prompt
            if system_prompt is not None
            else settings.openai_text_prompt or DEFAULT_TEXT_GENERATION_PROMPT
        )

    def generate_reply(
        self,
        *,
        session_id: UUID,
        transcript: str,
        conversation_history: list[ConversationTurn],
    ) -> TextGenerationResult:
        del session_id

        cleaned_transcript = transcript.strip()
        if not cleaned_transcript:
            raise TextGenerationServiceError("Transcript is empty.")

        history_lines = []
        for turn in conversation_history[-6:]:
            content = turn.content.strip()
            if not content:
                continue
            role = "사용자" if turn.role == "user" else "assistant"
            history_lines.append(f"{role}: {content}")

        history_block = "\n".join(history_lines) if history_lines else "(이전 대화 없음)"
        user_prompt = (
            "다음은 최근 대화입니다.\n"
            f"{history_block}\n\n"
            "방금 사용자 발화:\n"
            f"{cleaned_transcript}\n\n"
            "이제 다음 assistant 응답을 짧고 분명하게 작성해 주세요."
        )

        try:
            response = self.client.responses.create(
                model=self.model,
                instructions=self.system_prompt,
                input=user_prompt,
                temperature=0.7,
                max_output_tokens=160,
            )
        except (
            APIConnectionError,
            APIStatusError,
            APITimeoutError,
            RateLimitError,
        ) as exc:
            raise TextGenerationServiceError("OpenAI text generation request failed.") from exc
        except Exception as exc:  # pragma: no cover - defensive fallback
            raise TextGenerationServiceError("Unexpected text generation failure.") from exc

        text = (getattr(response, "output_text", None) or "").strip()
        if not text:
            raise TextGenerationServiceError("OpenAI returned an empty assistant reply.")

        return TextGenerationResult(
            text=text,
            safety_mode=False,
        )


class MockTextGenerationService(TextGenerationService):
    def generate_reply(
        self,
        *,
        session_id: UUID,
        transcript: str,
        conversation_history: list[ConversationTurn],
    ) -> TextGenerationResult:
        del session_id
        del conversation_history

        if not transcript.strip():
            raise TextGenerationServiceError("Transcript is empty.")

        return TextGenerationResult(
            text="말씀 감사합니다. 그때 가장 먼저 떠오르는 장소가 있나요?",
            safety_mode=False,
        )
