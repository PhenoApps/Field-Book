"""Minimal OIDC endpoints so Field Book can bypass auth."""

from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/.well-known/openid-configuration")
async def openid_configuration(request: Request):
    base = str(request.base_url).rstrip("/")
    return {
        "issuer": base,
        "authorization_endpoint": f"{base}/auth",
        "token_endpoint": f"{base}/token",
        "userinfo_endpoint": f"{base}/userinfo",
        "jwks_uri": f"{base}/jwks",
        "response_types_supported": ["token"],
        "subject_types_supported": ["public"],
        "id_token_signing_alg_values_supported": ["none"],
    }


@router.post("/token")
async def token():
    return {
        "access_token": "fieldbook-test-token",
        "token_type": "Bearer",
        "expires_in": 360000,
    }


@router.get("/userinfo")
async def userinfo():
    return {"sub": "janedoe", "name": "Jane Doe", "email": "jane@fieldbook.local"}


@router.get("/auth")
async def auth():
    return {"message": "use POST /token directly"}


@router.get("/jwks")
async def jwks():
    return {"keys": []}
