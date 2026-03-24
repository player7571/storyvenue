from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class SafetyCheckRequest(BaseModel):
    text: str = Field(min_length=1, max_length=5000)

    model_config = ConfigDict(str_strip_whitespace=True)


class SafetyCheckResponse(BaseModel):
    safety_mode: bool = False
    severity: Literal["low", "medium", "high"] = "low"
    reason: str
    recommended_action: str | None = None
