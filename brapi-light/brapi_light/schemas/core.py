"""Pydantic v2 schemas for core BrAPI entities."""

from pydantic import BaseModel, ConfigDict, field_validator


class ProgramSchema(BaseModel):
    programDbId: str
    programName: str
    programType: str | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")


class TrialSchema(BaseModel):
    trialDbId: str
    trialName: str
    trialDescription: str | None = None
    programDbId: str | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")


class StudySchema(BaseModel):
    studyDbId: str
    studyName: str
    studyDescription: str | None = None
    locationName: str | None = None
    commonCropName: str | None = None
    trialName: str | None = None
    programDbId: str | None = None
    active: str | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")

    @field_validator("active", mode="before")
    @classmethod
    def _coerce_active(cls, v):
        if isinstance(v, bool):
            return "true" if v else "false"
        return v


class SeasonSchema(BaseModel):
    seasonDbId: str
    season: str | None = None
    year: str | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")


class LocationSchema(BaseModel):
    locationDbId: str
    locationName: str
    country: str | None = None
    latitude: float | None = None
    longitude: float | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")


class PersonSchema(BaseModel):
    personDbId: str
    firstName: str | None = None
    lastName: str | None = None
    email: str | None = None
    model_config = ConfigDict(populate_by_name=True, from_attributes=True, extra="allow")
