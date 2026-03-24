from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    app_env: str = "local"
    supabase_url: str | None = None
    supabase_anon_key: str | None = None
    supabase_service_role_key: str | None = None
    openai_api_key: str | None = None
    openai_stt_model: str = "gpt-4o-transcribe"
    openai_stt_language: str | None = "ko"
    openai_stt_prompt: str | None = None
    openai_memory_model: str = "gpt-4.1-mini"
    openai_memory_prompt: str | None = None
    openai_chapter_model: str = "gpt-4.1-mini"
    openai_chapter_prompt: str | None = None
    openai_tts_model: str = "gpt-4o-mini-tts"
    openai_tts_voice: str = "coral"
    openai_tts_format: str = "mp3"
    openai_tts_public_path: str = "/generated-audio"
    openai_tts_output_dir: str = ".generated-audio"
    openai_tts_instructions: str | None = None

    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def openai_tts_output_dir_path(self) -> Path:
        output_dir = Path(self.openai_tts_output_dir)
        if not output_dir.is_absolute():
            output_dir = ENV_FILE.parent / output_dir
        return output_dir


@lru_cache
def get_settings() -> Settings:
    return Settings()
