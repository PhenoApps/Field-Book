"""BrAPI core endpoints: programs, trials, studies, people, seasons, locations."""

from math import ceil

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession

from brapi_light.database.base import get_db
from brapi_light.schemas.brapi_response import (
    BrAPIListResponse,
    BrAPISingleResponse,
    Metadata,
    Pagination,
    orm_to_camel,
)
from brapi_light.schemas.core import (
    LocationSchema,
    PersonSchema,
    ProgramSchema,
    SeasonSchema,
    StudySchema,
    TrialSchema,
)
from brapi_light.services import core as svc

router = APIRouter()


def _paginated_response(schema, items: list, total: int, page: int, page_size: int):
    return BrAPIListResponse(
        metadata=Metadata(
            pagination=Pagination(
                pageSize=page_size,
                currentPage=page,
                totalCount=total,
                totalPages=ceil(total / page_size) if total > 0 else 0,
            )
        ),
        result={"data": [schema.model_validate(orm_to_camel(it)) for it in items]},
    ).model_dump(by_alias=True)


# ── Programs ──────────────────────────────────────────────

@router.get("/brapi/v2/programs")
async def get_programs(
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_programs(db, page=page, page_size=pageSize)
    return _paginated_response(ProgramSchema, items, total, page, pageSize)


# ── Trials ────────────────────────────────────────────────

@router.get("/brapi/v2/trials")
async def get_trials(
    programDbId: str | None = Query(None, alias="programDbId"),
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_trials(
        db, program_db_id=programDbId, page=page, page_size=pageSize
    )
    return _paginated_response(TrialSchema, items, total, page, pageSize)


# ── Studies ───────────────────────────────────────────────

@router.get("/brapi/v2/studies")
async def get_studies(
    programDbId: str | None = Query(None, alias="programDbId"),
    trialDbId: str | None = Query(None, alias="trialDbId"),
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_studies(
        db,
        program_db_id=programDbId,
        trial_db_id=trialDbId,
        page=page,
        page_size=pageSize,
    )
    return _paginated_response(StudySchema, items, total, page, pageSize)


@router.get("/brapi/v2/studies/{study_db_id}")
async def get_study(
    study_db_id: str,
    db: AsyncSession = Depends(get_db),
):
    study = await svc.get_study(db, study_db_id)
    if study is None:
        raise HTTPException(status_code=404, detail="Study not found")
    return BrAPISingleResponse(
        result=StudySchema.model_validate(orm_to_camel(study)),
    ).model_dump(by_alias=True)


# ── Seasons ───────────────────────────────────────────────

@router.get("/brapi/v2/seasons")
async def get_seasons(
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_seasons(db, page=page, page_size=pageSize)
    return _paginated_response(SeasonSchema, items, total, page, pageSize)


# ── Locations ─────────────────────────────────────────────

@router.get("/brapi/v2/locations")
async def get_locations(
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_locations(db, page=page, page_size=pageSize)
    return _paginated_response(LocationSchema, items, total, page, pageSize)


# ── People ────────────────────────────────────────────────

@router.get("/brapi/v2/people")
async def get_people(
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_people(db, page=page, page_size=pageSize)
    return _paginated_response(PersonSchema, items, total, page, pageSize)
