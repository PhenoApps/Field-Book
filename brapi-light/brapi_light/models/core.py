"""SQLAlchemy ORM models for core entities: Program, Trial, Study, Season, Location, Person."""

import uuid

from sqlalchemy import ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from brapi_light.database.base import Base


def _uuid() -> str:
    return uuid.uuid4().hex


class Program(Base):
    __tablename__ = "program"

    program_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    program_name: Mapped[str] = mapped_column(String, nullable=False)
    program_type: Mapped[str | None] = mapped_column(String, nullable=True)

    trials: Mapped[list["Trial"]] = relationship(back_populates="program", lazy="selectin")
    studies: Mapped[list["Study"]] = relationship(back_populates="program", lazy="selectin")


class Trial(Base):
    __tablename__ = "trial"

    trial_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    trial_name: Mapped[str] = mapped_column(String, nullable=False)
    trial_description: Mapped[str | None] = mapped_column(String, nullable=True)
    program_db_id: Mapped[str] = mapped_column(ForeignKey("program.program_db_id"), nullable=False)

    program: Mapped[Program] = relationship(back_populates="trials")
    studies: Mapped[list["Study"]] = relationship(back_populates="trial", lazy="selectin")


class Study(Base):
    __tablename__ = "study"

    study_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    study_name: Mapped[str] = mapped_column(String, nullable=False)
    study_description: Mapped[str | None] = mapped_column(String, nullable=True)
    location_name: Mapped[str | None] = mapped_column(String, nullable=True)
    common_crop_name: Mapped[str | None] = mapped_column(String, nullable=True)
    active: Mapped[bool] = mapped_column(default=True)
    program_db_id: Mapped[str] = mapped_column(ForeignKey("program.program_db_id"), nullable=False)
    trial_db_id: Mapped[str] = mapped_column(ForeignKey("trial.trial_db_id"), nullable=False)

    program: Mapped[Program] = relationship(back_populates="studies")
    trial: Mapped[Trial] = relationship(back_populates="studies")


class Season(Base):
    __tablename__ = "season"

    season_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    season: Mapped[str | None] = mapped_column(String, nullable=True)
    year: Mapped[str | None] = mapped_column(String, nullable=True)


class Location(Base):
    __tablename__ = "location"

    location_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    location_name: Mapped[str] = mapped_column(String, nullable=False)
    country: Mapped[str | None] = mapped_column(String, nullable=True)
    latitude: Mapped[float | None] = mapped_column(nullable=True)
    longitude: Mapped[float | None] = mapped_column(nullable=True)


class Person(Base):
    __tablename__ = "person"

    person_db_id: Mapped[str] = mapped_column(String, primary_key=True, default=_uuid)
    first_name: Mapped[str | None] = mapped_column(String, nullable=True)
    last_name: Mapped[str | None] = mapped_column(String, nullable=True)
    email: Mapped[str | None] = mapped_column(String, nullable=True)
