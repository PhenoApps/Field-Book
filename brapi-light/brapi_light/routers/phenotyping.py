"""BrAPI phenotyping endpoints: observationunits, variables, observations, images."""

from math import ceil
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy.ext.asyncio import AsyncSession

from brapi_light.database.base import get_db
from brapi_light.models.phenotyping import Image, Observation
from brapi_light.schemas.brapi_response import (
    BrAPIListResponse,
    BrAPISingleResponse,
    Metadata,
    Pagination,
    camel_dict_to_snake,
    orm_to_camel,
)
from brapi_light.schemas.phenotyping import (
    ImageSchema,
    ObservationSchema,
    ObservationUnitSchema,
    ObservationVariableSchema,
)
from brapi_light.services import phenotyping as svc

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


# ── Observation Units ─────────────────────────────────────

@router.get("/brapi/v2/observationunits")
async def get_observation_units(
    studyDbId: str | None = Query(None, alias="studyDbId"),
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_observation_units(
        db, study_db_id=studyDbId, page=page, page_size=pageSize
    )
    return _paginated_response(ObservationUnitSchema, items, total, page, pageSize)


# ── Variables ──────────────────────────────────────────────

@router.get("/brapi/v2/variables")
async def get_variables(
    studyDbId: str | None = Query(None, alias="studyDbId"),
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_variables(
        db, study_db_id=studyDbId, page=page, page_size=pageSize
    )
    return _paginated_response(ObservationVariableSchema, items, total, page, pageSize)


# ── Observations ───────────────────────────────────────────

@router.get("/brapi/v2/observations")
async def get_observations(
    studyDbId: str | None = Query(None, alias="studyDbId"),
    observationVariableDbId: str | None = Query(None, alias="observationVariableDbId"),
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_observations(
        db,
        study_db_id=studyDbId,
        observation_variable_db_id=observationVariableDbId,
        page=page,
        page_size=pageSize,
    )
    return _paginated_response(ObservationSchema, items, total, page, pageSize)


@router.post("/brapi/v2/observations")
async def post_observations(
    body: list[dict[str, Any]],
    db: AsyncSession = Depends(get_db),
):
    observations = [Observation(**camel_dict_to_snake(obs)) for obs in body]
    created = await svc.create_observations(db, observations)
    return BrAPIListResponse(
        metadata=Metadata(),
        result={"data": [ObservationSchema.model_validate(orm_to_camel(o)) for o in created]},
    ).model_dump(by_alias=True)


@router.put("/brapi/v2/observations")
async def put_observations(
    body: dict[str, dict[str, Any]],
    db: AsyncSession = Depends(get_db),
):
    snake_body = {k: camel_dict_to_snake(v) for k, v in body.items()}
    updated, conflicts = await svc.update_observations(db, snake_body)
    result = [ObservationSchema.model_validate(orm_to_camel(o)) for o in updated]
    if conflicts:
        conflict_info = [{"observationDbId": cid, "message": "rev mismatch"} for cid in conflicts]
        return BrAPIListResponse(
            metadata=Metadata(status=[{"conflicts": conflict_info}]),
            result={"data": result},
        ).model_dump(by_alias=True)
    return BrAPIListResponse(
        metadata=Metadata(),
        result={"data": result},
    ).model_dump(by_alias=True)


# ── Sync Changes ──────────────────────────────────────────

@router.get("/brapi/v2/sync/changes")
async def get_sync_changes(
    since: str = Query(..., alias="since"),
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    items, total = await svc.list_changed_observations(
        db, since=since, page=page, page_size=pageSize
    )
    return _paginated_response(ObservationSchema, items, total, page, pageSize)


# ── Images ─────────────────────────────────────────────────

@router.post("/brapi/v2/images")
async def post_images(
    body: list[dict[str, Any]],
    db: AsyncSession = Depends(get_db),
):
    images = [Image(**camel_dict_to_snake(img)) for img in body]
    created = await svc.create_images(db, images)
    return BrAPIListResponse(
        metadata=Metadata(),
        result={"data": [ImageSchema.model_validate(orm_to_camel(i)) for i in created]},
    ).model_dump(by_alias=True)


@router.put("/brapi/v2/images/{image_db_id}/imagecontent")
async def put_image_content(
    image_db_id: str,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    content = await request.body()
    img = await svc.update_image_content(db, image_db_id, content)
    if img is None:
        raise HTTPException(status_code=404, detail="Image not found")
    data = orm_to_camel(img)
    data.pop("content", None)  # binary, not JSON-serializable
    return BrAPISingleResponse(
        result=ImageSchema.model_validate(data),
    ).model_dump(by_alias=True)
