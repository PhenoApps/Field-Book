"""CRUD operations for phenotyping entities."""

import json
from datetime import datetime, timezone

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from brapi_light.models.phenotyping import (
    Image,
    Observation,
    ObservationUnit,
    ObservationVariable,
)


async def _list_all(
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


async def list_variables(
    db: AsyncSession, study_db_id: str | None = None,
    page: int = 0, page_size: int = 1000,
) -> tuple[list[ObservationVariable], int]:
    return await _list_all(db, ObservationVariable, page=page, page_size=page_size,
                           study_db_id=study_db_id)


async def list_observation_units(
    db: AsyncSession, study_db_id: str | None = None,
    page: int = 0, page_size: int = 1000,
) -> tuple[list[ObservationUnit], int]:
    return await _list_all(db, ObservationUnit, page=page, page_size=page_size,
                           study_db_id=study_db_id)


async def list_observations(
    db: AsyncSession, study_db_id: str | None = None,
    observation_variable_db_id: str | None = None,
    page: int = 0, page_size: int = 1000,
) -> tuple[list[Observation], int]:
    items, total = await _list_all(db, Observation, page=page, page_size=10000,
                                   study_db_id=study_db_id,
                                   observation_variable_db_id=observation_variable_db_id)
    # Deduplicate: keep only the latest record per (unit, variable) pair
    seen: dict[tuple[str, str], Observation] = {}
    for obs in sorted(items, key=lambda o: o.last_synced_time or ""):
        key = (obs.observation_unit_db_id, obs.observation_variable_db_id or "")
        seen[key] = obs  # later entries overwrite earlier ones
    deduped = list(seen.values())
    total = len(deduped)
    start = page * page_size
    return deduped[start:start + page_size], total


async def resolve_or_create_variable(
    db: AsyncSession,
    variable_name: str | None,
    study_db_id: str | None,
) -> str | None:
    if not variable_name:
        return None
    stmt = select(ObservationVariable).where(
        ObservationVariable.observation_variable_name == variable_name
    )
    existing = (await db.execute(stmt)).scalars().first()
    if existing is not None:
        return existing.observation_variable_db_id
    new_var = ObservationVariable(
        observation_variable_name=variable_name,
        study_db_id=study_db_id,
        trait=json.dumps({"traitName": variable_name}),
    )
    db.add(new_var)
    await db.flush()
    return new_var.observation_variable_db_id


async def ensure_variable_exists(
    db: AsyncSession,
    variable_db_id: str,
    variable_name: str | None = None,
    study_db_id: str | None = None,
) -> None:
    """Ensure an ObservationVariable with the given ID exists. Creates one if missing."""
    stmt = select(ObservationVariable).where(
        ObservationVariable.observation_variable_db_id == variable_db_id
    )
    existing = (await db.execute(stmt)).scalars().first()
    if existing is not None:
        return
    db.add(ObservationVariable(
        observation_variable_db_id=variable_db_id,
        observation_variable_name=variable_name or variable_db_id[:8],
        study_db_id=study_db_id,
        trait=json.dumps({"traitName": variable_name or variable_db_id[:8]}),
    ))
    await db.flush()


async def create_observations(db: AsyncSession, observations: list[Observation]) -> list[Observation]:
    now = datetime.now(timezone.utc).isoformat()
    for obs in observations:
        obs.rev = 1
        obs.last_synced_time = now
        db.add(obs)
    await db.commit()
    return observations


async def update_observations(db: AsyncSession, updates: dict[str, dict]) -> tuple[list[Observation], list[str]]:
    result = []
    conflicts = []
    now = datetime.now(timezone.utc).isoformat()
    for obs_db_id, fields in updates.items():
        obs = (await db.execute(
            select(Observation).where(Observation.observation_db_id == obs_db_id)
        )).scalar_one_or_none()
        if obs is None:
            continue
        expected_rev = fields.pop("rev", None)
        if expected_rev is not None and expected_rev != obs.rev:
            conflicts.append(obs_db_id)
            continue
        for key, val in fields.items():
            if hasattr(obs, key):
                setattr(obs, key, val)
        obs.rev += 1
        obs.last_synced_time = now
        result.append(obs)
    await db.commit()
    return result, conflicts


async def list_changed_observations(
    db: AsyncSession, since: str, page: int = 0, page_size: int = 1000,
) -> tuple[list[Observation], int]:
    stmt = select(func.count()).select_from(Observation).where(
        Observation.last_synced_time > since
    )
    total = (await db.execute(stmt)).scalar() or 0
    query = (
        select(Observation)
        .where(Observation.last_synced_time > since)
        .offset(page * page_size)
        .limit(page_size)
    )
    items = (await db.execute(query)).scalars().all()
    return list(items), total


async def create_images(db: AsyncSession, images: list[Image]) -> list[Image]:
    for img in images:
        db.add(img)
    await db.commit()
    return images


async def get_image(db: AsyncSession, image_db_id: str) -> Image | None:
    return (await db.execute(
        select(Image).where(Image.image_db_id == image_db_id)
    )).scalar_one_or_none()


async def update_image_content(db: AsyncSession, image_db_id: str, content: bytes) -> Image | None:
    img = await get_image(db, image_db_id)
    if img is not None:
        img.content = content
        await db.commit()
    return img
