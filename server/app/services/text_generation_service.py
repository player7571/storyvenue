from dataclasses import dataclass
from uuid import UUID


class TextGenerationServiceError(Exception):
    """Raised when assistant text generation fails."""


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
    ) -> TextGenerationResult:
        raise NotImplementedError


class MockTextGenerationService(TextGenerationService):
    def generate_reply(
        self,
        *,
        session_id: UUID,
        transcript: str,
    ) -> TextGenerationResult:
        del session_id

        if not transcript.strip():
            raise TextGenerationServiceError("Transcript is empty.")

        # TODO: replace this mock with prompt-driven assistant generation.
        return TextGenerationResult(
            text="말씀 감사합니다. 그때 가장 먼저 떠오르는 장소가 있나요?",
            safety_mode=False,
        )
