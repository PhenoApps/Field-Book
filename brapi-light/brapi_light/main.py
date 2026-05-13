"""brapi-light — Lightweight BrAPI v2 backend for Field Book."""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from brapi_light.database.base import Base
from brapi_light.database.engine import engine
from brapi_light.models import core as _core_models  # noqa: F401  register ORM models
from brapi_light.routers import auth, core, phenotyping, server_info


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield


app = FastAPI(
    title="brapi-light",
    description="Lightweight BrAPI v2 backend for collaborative field phenotyping",
    version="0.1.0",
    lifespan=lifespan,
)

app.include_router(server_info.router)
app.include_router(core.router)
app.include_router(auth.router)
app.include_router(phenotyping.router)
