"""Declarative base and FastAPI dependency for database sessions."""

from collections.abc import AsyncGenerator

from sqlalchemy.orm import DeclarativeBase

from brapi_light.database.engine import async_session_factory


class Base(DeclarativeBase):
    pass


async def get_db() -> AsyncGenerator:
    async with async_session_factory() as session:
        yield session
