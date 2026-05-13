"""GET /brapi/v2/serverinfo — service discovery endpoint."""

from fastapi import APIRouter

from brapi_light.config import settings

router = APIRouter()


EXPECTED_CALLS = [
    {
        "service": "serverinfo",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "programs",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "trials",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "studies",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "observationunits",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "variables",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "observations",
        "versions": ["2.0", "2.1"],
        "methods": ["GET", "POST", "PUT"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "seasons",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "locations",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "people",
        "versions": ["2.0", "2.1"],
        "methods": ["GET"],
        "dataTypes": ["application/json"],
    },
    {
        "service": "images",
        "versions": ["2.0", "2.1"],
        "methods": ["POST", "PUT"],
        "dataTypes": ["application/json"],
    },
]


@router.get("/brapi/v2/serverinfo")
async def get_server_info():
    return {
        "result": {
            "serverName": settings.server_name,
            "serverDescription": "brapi-light — Lightweight BrAPI v2 backend for Field Book",
            "serverVersion": "0.1.0",
            "organizationName": "Field Book",
            "organizationUrl": "https://github.com/PhenoApps/Field-Book",
            "contactEmail": settings.contact_email,
            "calls": EXPECTED_CALLS,
        }
    }
