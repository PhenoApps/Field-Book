"""Application configuration via environment variables."""

import os


class Settings:
    database_url: str = os.getenv(
        "DATABASE_URL",
        "sqlite+aiosqlite:///./brapi.db",
    )
    brapi_version: str = "2.1"
    server_name: str = "brapi-light"
    contact_email: str = os.getenv("CONTACT_EMAIL", "admin@fieldbook.local")


settings = Settings()
