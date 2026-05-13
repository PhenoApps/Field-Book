"""SQLAlchemy ORM models for phenotyping: ObservationUnit, ObservationVariable, Observation, Image."""

import uuid

from sqlalchemy import ForeignKey, LargeBinary, String
from sqlalchemy.orm import Mapped, mapped_column

from brapi_light.database.base import Base


def _uuid() -> str:
    return uuid.uuid4().hex


class ObservationVariable(Base):
    __tablename__ = "observation_variable"

    observation_variable_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    observation_variable_name: Mapped[str] = mapped_column(String, nullable=False)
    trait: Mapped[str | None] = mapped_column(String, nullable=True)       # JSON string
    scale: Mapped[str | None] = mapped_column(String, nullable=True)       # JSON string
    default_value: Mapped[str | None] = mapped_column(String, nullable=True)
    synonyms: Mapped[str | None] = mapped_column(String, nullable=True)    # JSON array string
    study_db_id: Mapped[str | None] = mapped_column(ForeignKey("study.study_db_id"), nullable=True)


class ObservationUnit(Base):
    __tablename__ = "observation_unit"

    observation_unit_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    study_db_id: Mapped[str] = mapped_column(ForeignKey("study.study_db_id"), nullable=False)
    observation_unit_name: Mapped[str | None] = mapped_column(String, nullable=True)
    germplasm_name: Mapped[str | None] = mapped_column(String, nullable=True)
    germplasm_db_id: Mapped[str | None] = mapped_column(String, nullable=True)
    observation_unit_position: Mapped[str | None] = mapped_column(String, nullable=True)  # JSON


class Observation(Base):
    __tablename__ = "observation"

    observation_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    observation_unit_db_id: Mapped[str] = mapped_column(ForeignKey("observation_unit.observation_unit_db_id"), nullable=False)
    observation_variable_db_id: Mapped[str | None] = mapped_column(ForeignKey("observation_variable.observation_variable_db_id"), nullable=True)
    study_db_id: Mapped[str] = mapped_column(ForeignKey("study.study_db_id"), nullable=False)
    observation_variable_name: Mapped[str | None] = mapped_column(String, nullable=True)
    value: Mapped[str] = mapped_column(String, nullable=False)
    collector: Mapped[str | None] = mapped_column(String, nullable=True)
    observation_time_stamp: Mapped[str | None] = mapped_column(String, nullable=True)
    season: Mapped[str | None] = mapped_column(String, nullable=True)
    external_references: Mapped[str | None] = mapped_column(String, nullable=True)
    rev: Mapped[int] = mapped_column(default=1)
    last_synced_time: Mapped[str | None] = mapped_column(String, nullable=True)


class Image(Base):
    __tablename__ = "image"

    image_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    observation_unit_db_id: Mapped[str | None] = mapped_column(ForeignKey("observation_unit.observation_unit_db_id"), nullable=True)
    image_file_name: Mapped[str | None] = mapped_column(String, nullable=True)
    image_name: Mapped[str | None] = mapped_column(String, nullable=True)
    image_file_size: Mapped[int | None] = mapped_column(nullable=True)
    image_width: Mapped[int | None] = mapped_column(nullable=True)
    image_height: Mapped[int | None] = mapped_column(nullable=True)
    mime_type: Mapped[str | None] = mapped_column(String, nullable=True)
    content: Mapped[bytes | None] = mapped_column(LargeBinary, nullable=True)
    description: Mapped[str | None] = mapped_column(String, nullable=True)
    image_time_stamp: Mapped[str | None] = mapped_column(String, nullable=True)
    copyright: Mapped[str | None] = mapped_column(String, nullable=True)
    additional_info: Mapped[str | None] = mapped_column(String, nullable=True)
