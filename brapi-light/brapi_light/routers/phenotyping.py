"""BrAPI phenotyping endpoints: observationunits, variables, observations, images."""

import json
from math import ceil
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy.ext.asyncio import AsyncSession

from brapi_light.database.base import get_db
from brapi_light.models.phenotyping import Image, Observation, ObservationVariable
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


# ── Observation Levels ────────────────────────────────────

@router.get("/brapi/v2/observationlevels")
async def get_observation_levels():
    return BrAPIListResponse(
        metadata=Metadata(),
        result={"data": [
            {"levelName": "plot", "levelOrder": 0, "levelCode": "PLOT"},
            {"levelName": "plant", "levelOrder": 1, "levelCode": "PLANT"},
            {"levelName": "subplot", "levelOrder": 2, "levelCode": "SUBPLOT"},
        ]},
    ).model_dump(by_alias=True)


# ── Germplasm (minimal) ────────────────────────────────────

@router.get("/brapi/v2/germplasm")
async def get_germplasm(
    studyDbId: str | None = Query(None, alias="studyDbId"),
    page: int = Query(0, ge=0),
    pageSize: int = Query(1000, ge=1, le=10000, alias="pageSize"),
    db: AsyncSession = Depends(get_db),
):
    """Return basic germplasm entries for the given study."""
    return BrAPIListResponse(
        metadata=Metadata(pagination=Pagination(pageSize=pageSize, currentPage=page)),
        result={"data": [
            {"germplasmDbId": "g1", "germplasmName": "Variety_1", "species": "Triticum aestivum"},
            {"germplasmDbId": "g2", "germplasmName": "Variety_2", "species": "Triticum aestivum"},
            {"germplasmDbId": "g3", "germplasmName": "Variety_3", "species": "Triticum aestivum"},
            {"germplasmDbId": "g4", "germplasmName": "Variety_4", "species": "Triticum aestivum"},
            {"germplasmDbId": "g5", "germplasmName": "Variety_5", "species": "Triticum aestivum"},
        ]},
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
    allowed = {c.name for c in Observation.__table__.columns}
    json_cols = {"external_references"}
    observations = []
    for obs_dict in body:
        snake = camel_dict_to_snake(obs_dict)
        # Handle BrAPI nested objects -> string
        if isinstance(snake.get("season"), dict):
            s = snake["season"]
            snake["season"] = s.get("seasonName") or s.get("season") or str(s.get("year", ""))
        # Auto-create / ensure variable exists for both local and BrAPI traits
        var_id = snake.get("observation_variable_db_id")
        if not var_id:
            resolved_id = await svc.resolve_or_create_variable(
                db,
                variable_name=snake.get("observation_variable_name"),
                study_db_id=snake.get("study_db_id"),
            )
            if resolved_id is not None:
                snake["observation_variable_db_id"] = resolved_id
        else:
            await svc.ensure_variable_exists(
                db, var_id,
                variable_name=snake.get("observation_variable_name"),
                study_db_id=snake.get("study_db_id"),
            )
        # Drop client-side observationDbId to avoid UNIQUE conflicts on re-upload
        snake.pop("observation_db_id", None)
        filtered = {}
        for k, v in snake.items():
            if k not in allowed:
                continue
            if k in json_cols and isinstance(v, (list, dict)):
                filtered[k] = json.dumps(v) if v else None
            else:
                filtered[k] = v
        observations.append(Observation(**filtered))
    created = await svc.create_observations(db, observations)
    return BrAPIListResponse(
        metadata=Metadata(pagination=Pagination(
            pageSize=len(created), currentPage=0,
            totalCount=len(created), totalPages=1,
        )),
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
    p = Pagination(pageSize=len(result), currentPage=0, totalCount=len(result), totalPages=1)
    if conflicts:
        conflict_info = [{"observationDbId": cid, "message": "rev mismatch"} for cid in conflicts]
        return BrAPIListResponse(
            metadata=Metadata(pagination=p, status=[{"conflicts": conflict_info}]),
            result={"data": result},
        ).model_dump(by_alias=True)
    return BrAPIListResponse(
        metadata=Metadata(pagination=p),
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
