"""Read-only CRUD operations for core BrAPI entities."""

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from brapi_light.models.core import Location, Person, Program, Season, Study, Trial


async def list_all(
    db: AsyncSession, model, page: int = 0, page_size: int = 1000, **filters
) -> tuple[list, int]:
    stmt = select(func.count()).select_from(model)
    for k, v in filters.items():
        if v is not None:
            stmt = stmt.where(getattr(model, k) == v)
    total = (await db.execute(stmt)).scalar() or 0

    query = select(model).offset(page * page_size).limit(page_size)
    for k, v in filters.items():
        if v is not None:
            query = query.where(getattr(model, k) == v)
    items = (await db.execute(query)).scalars().all()
    return list(items), total


async def list_programs(
    db: AsyncSession, page: int = 0, page_size: int = 1000
) -> tuple[list[Program], int]:
    return await list_all(db, Program, page=page, page_size=page_size)


async def list_trials(
    db: AsyncSession, program_db_id: str | None = None,
    page: int = 0, page_size: int = 1000,
) -> tuple[list[Trial], int]:
    return await list_all(db, Trial, page=page, page_size=page_size,
                          program_db_id=program_db_id)


async def list_studies(
    db: AsyncSession, program_db_id: str | None = None,
    trial_db_id: str | None = None,
    page: int = 0, page_size: int = 1000,
) -> tuple[list[Study], int]:
    return await list_all(db, Study, page=page, page_size=page_size,
                          program_db_id=program_db_id, trial_db_id=trial_db_id)


async def get_study(db: AsyncSession, study_db_id: str) -> Study | None:
    stmt = select(Study).where(Study.study_db_id == study_db_id)
    return (await db.execute(stmt)).scalar_one_or_none()


async def list_seasons(
    db: AsyncSession, page: int = 0, page_size: int = 1000
) -> tuple[list[Season], int]:
    return await list_all(db, Season, page=page, page_size=page_size)


async def list_locations(
    db: AsyncSession, page: int = 0, page_size: int = 1000
) -> tuple[list[Location], int]:
    return await list_all(db, Location, page=page, page_size=page_size)


async def list_people(
    db: AsyncSession, page: int = 0, page_size: int = 1000
) -> tuple[list[Person], int]:
    return await list_all(db, Person, page=page, page_size=page_size)
