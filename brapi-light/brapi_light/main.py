"""brapi-light — Lightweight BrAPI v2 backend for Field Book."""

import json
import traceback
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from sqlalchemy import select

from brapi_light.database.base import Base
from brapi_light.database.engine import async_session_factory, engine
from brapi_light.models import core as _core_models  # noqa: F401  register ORM models
from brapi_light.models.core import Program, Study, Trial
from brapi_light.models.phenotyping import ObservationVariable
from brapi_light.routers import auth, core, phenotyping, server_info


async def _seed_demo_data():
    from brapi_light.models.core import Location, Person, Season
    async with async_session_factory() as db:
        result = await db.execute(select(Study).limit(1))
        if result.scalar_one_or_none() is not None:
            return  # already seeded

        p = Program(program_db_id="p1", program_name="Wheat Breeding 2026")
        t = Trial(trial_db_id="t1", trial_name="Drought Tolerance", program_db_id="p1")
        s = Study(
            study_db_id="s1", study_name="Field A", common_crop_name="Wheat",
            location_name="Test", active=True, program_db_id="p1", trial_db_id="t1",
        )
        loc = Location(location_db_id="l1", location_name="Test Field")
        season = Season(season_db_id="season1", season="2026", year="2026")
        person = Person(person_db_id="person1", first_name="Jane", last_name="Doe", email="janedoe@test.local")
        db.add_all([p, t, s, loc, season, person])
        await db.flush()

        variables = [
            ObservationVariable(
                observation_variable_db_id="v1",
                observation_variable_name="Plant Height",
                study_db_id="s1",
                trait=json.dumps({"traitDbId": "t1", "traitName": "Plant Height", "traitDescription": "Plant height measured in cm"}),
                scale=json.dumps({"dataType": "Numerical", "validValues": {"minimumValue": "0", "maximumValue": "300"}}),
            ),
            ObservationVariable(
                observation_variable_db_id="v2",
                observation_variable_name="Grain Yield",
                study_db_id="s1",
                trait=json.dumps({"traitDbId": "t2", "traitName": "Grain Yield", "traitDescription": "Grain yield in kg/ha"}),
                scale=json.dumps({"dataType": "Numerical", "validValues": {"minimumValue": "0", "maximumValue": "10000"}}),
            ),
            ObservationVariable(
                observation_variable_db_id="v3",
                observation_variable_name="Leaf Color",
                study_db_id="s1",
                trait=json.dumps({"traitDbId": "t3", "traitName": "Leaf Color", "traitDescription": "Leaf color assessment"}),
                scale=json.dumps({"dataType": "Categorical", "validValues": {"categories": [{"value": "Green"}, {"value": "Yellow"}, {"value": "Brown"}]}}),
            ),
        ]
        db.add_all(variables)
        await db.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    await _seed_demo_data()
    yield


app = FastAPI(
    title="brapi-light",
    description="Lightweight BrAPI v2 backend for collaborative field phenotyping",
    version="0.1.0",
    lifespan=lifespan,
)


@app.middleware("http")
async def log_errors(request: Request, call_next):
    try:
        response = await call_next(request)
        return response
    except Exception:
        body = None
        try:
            body = await request.body()
            body = json.loads(body)
        except Exception:
            body = str(body)
        msg = (
            f"\n{'='*60}\n"
            f"500 ERROR on {request.method} {request.url.path}\n"
            f"Body: {json.dumps(body, indent=2, default=str)[:3000]}\n"
            f"Traceback:\n{traceback.format_exc()}\n"
            f"{'='*60}\n"
        )
        log_path = __file__.rsplit("brapi_light", 1)[0] + "brapi_errors.log"
        with open(log_path, "a") as f:
            f.write(msg)
        print(msg)
        return JSONResponse(status_code=500, content={"detail": "Internal Server Error"})


from brapi_light.routers import admin

app.include_router(server_info.router)
app.include_router(core.router)
app.include_router(auth.router)
app.include_router(phenotyping.router)
app.include_router(admin.router)
