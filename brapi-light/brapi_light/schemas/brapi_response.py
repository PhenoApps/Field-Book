"""BrAPI v2 response wrappers — Metadata, Pagination, ListResponse, SingleResponse."""

from typing import Any, Generic, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class Pagination(BaseModel):
    pageSize: int = Field(default=1000)
    currentPage: int = Field(default=0)
    totalCount: int = Field(default=0)
    totalPages: int = Field(default=0)


class Metadata(BaseModel):
    pagination: Pagination | None = None
    status: list[dict] = Field(default_factory=list)
    datafiles: list[str] = Field(default_factory=list)


class BrAPIListResponse(BaseModel, Generic[T]):
    metadata: Metadata = Field(default_factory=Metadata)
    result: dict[str, list[T]]


class BrAPISingleResponse(BaseModel, Generic[T]):
    metadata: Metadata = Field(default_factory=Metadata)
    result: T


def orm_to_camel(obj: Any) -> dict[str, Any]:
    """Convert a SQLAlchemy ORM object to a camelCase dict for Pydantic validation."""
    result = {}
    for attr in obj.__mapper__.column_attrs:
        key = attr.key
        camel_key = _to_camel_case(key)
        result[camel_key] = getattr(obj, key)
    return result


def _to_camel_case(snake: str) -> str:
    parts = snake.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def camel_to_snake(camel: str) -> str:
    """Convert camelCase to snake_case."""
    result = []
    for ch in camel:
        if ch.isupper():
            result.append("_")
            result.append(ch.lower())
        else:
            result.append(ch)
    return "".join(result)


def camel_dict_to_snake(d: dict) -> dict:
    """Convert dict keys from camelCase to snake_case."""
    return {camel_to_snake(k): v for k, v in d.items()}
