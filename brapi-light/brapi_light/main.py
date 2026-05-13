"""brapi-light — Lightweight BrAPI v2 backend for Field Book."""

import json
import traceback
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

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


app.include_router(server_info.router)
app.include_router(core.router)
app.include_router(auth.router)
app.include_router(phenotyping.router)
