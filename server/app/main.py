from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.api.router import api_router
from app.core.config import get_settings


def create_app() -> FastAPI:
    settings = get_settings()
    tts_output_dir = settings.openai_tts_output_dir_path
    tts_output_dir.mkdir(parents=True, exist_ok=True)

    app = FastAPI(
        title="StoryVenue API",
        version="0.1.0",
    )
    app.mount(
        settings.openai_tts_public_path,
        StaticFiles(directory=tts_output_dir),
        name="generated-audio",
    )
    app.include_router(api_router)
    return app


app = create_app()
