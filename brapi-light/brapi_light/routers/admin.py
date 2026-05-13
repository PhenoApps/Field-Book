"""Admin endpoints for database browsing (not part of BrAPI spec)."""

import os

from fastapi import APIRouter, Depends, Query, Request
from fastapi.templating import Jinja2Templates
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from brapi_light.database.base import get_db

router = APIRouter()

_templates_dir = os.path.join(os.path.dirname(__file__), "..", "templates")
templates = Jinja2Templates(directory=os.path.abspath(_templates_dir))


@router.get("/admin/db", include_in_schema=False)
async def browse_database(
    request: Request,
    table: str | None = Query(None),
    db: AsyncSession = Depends(get_db),
):
    # List all table names via SQLite master table
    result = await db.execute(
        text("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
    )
    tables = [row[0] for row in result.fetchall()]

    table_data = None
    if table and table in tables:
        result = await db.execute(text(f'SELECT * FROM "{table}"'))
        rows = result.mappings().all()
        columns = list(rows[0].keys()) if rows else []
        table_data = {
            "name": table,
            "columns": columns,
            "rows": [list(r.values()) for r in rows],
        }

    return templates.TemplateResponse(
        request=request, name="db_browser.html",
        context={"tables": tables, "active_table": table_data},
    )
