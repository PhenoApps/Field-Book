"""Pydantic v2 schemas for phenotyping BrAPI entities."""

import json

from pydantic import BaseModel, ConfigDict, field_validator


def _parse_json(v):
    if isinstance(v, str):
        return json.loads(v)
    return v


class ObservationVariableSchema(BaseModel):
    observationVariableDbId: str
    observationVariableName: str
    trait: dict | None = None
    scale: dict | None = None
    defaultValue: str | None = None
    synonyms: list[str] | None = None
    additionalInfo: dict | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")

    @field_validator("trait", "scale", "additionalInfo", mode="before")
    @classmethod
    def _parse_json_dict(cls, v):
        return _parse_json(v) if v is not None else None

    @field_validator("synonyms", mode="before")
    @classmethod
    def _parse_json_list(cls, v):
        return _parse_json(v) if v is not None else None


class ObservationUnitSchema(BaseModel):
    observationUnitDbId: str
    observationUnitName: str | None = None
    germplasmName: str | None = None
    germplasmDbId: str | None = None
    observationUnitPosition: dict | None = None
    additionalInfo: dict | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")

    @field_validator("observationUnitPosition", "additionalInfo", mode="before")
    @classmethod
    def _parse_json(cls, v):
        return _parse_json(v) if v is not None else None


class ObservationSchema(BaseModel):
    observationDbId: str | None = None
    observationUnitDbId: str
    observationVariableDbId: str | None = None
    observationVariableName: str | None = None
    value: str
    collector: str | None = None
    observationTimeStamp: str | None = None
    studyDbId: str | None = None
    season: str | None = None
    externalReferences: list | None = None
    rev: int | None = None
    lastSyncedTime: str | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")

    @field_validator("externalReferences", mode="before")
    @classmethod
    def _parse_json(cls, v):
        return _parse_json(v) if v is not None else None


class ImageSchema(BaseModel):
    imageDbId: str | None = None
    observationUnitDbId: str | None = None
    imageFileName: str | None = None
    imageName: str | None = None
    imageFileSize: int | None = None
    imageWidth: int | None = None
    imageHeight: int | None = None
    mimeType: str | None = None
    imageTimeStamp: str | None = None
    description: str | None = None
    descriptiveOntologyTerms: list[str] | None = None
    copyright: str | None = None
    additionalInfo: dict | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")

    @field_validator("descriptiveOntologyTerms", "additionalInfo", mode="before")
    @classmethod
    def _parse_json(cls, v):
        return _parse_json(v) if v is not None else None
