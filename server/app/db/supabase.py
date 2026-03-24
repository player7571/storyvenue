from functools import lru_cache

from supabase import Client, create_client

from app.core.config import get_settings


def _build_client(key: str | None, key_name: str) -> Client:
    settings = get_settings()

    if not settings.supabase_url:
        raise RuntimeError("SUPABASE_URL is not configured.")
    if not key:
        raise RuntimeError(f"{key_name} is not configured.")

    return create_client(settings.supabase_url, key)


@lru_cache
def get_supabase_anon_client() -> Client:
    settings = get_settings()
    return _build_client(settings.supabase_anon_key, "SUPABASE_ANON_KEY")


@lru_cache
def get_supabase_service_role_client() -> Client:
    settings = get_settings()
    return _build_client(
        settings.supabase_service_role_key,
        "SUPABASE_SERVICE_ROLE_KEY",
    )
