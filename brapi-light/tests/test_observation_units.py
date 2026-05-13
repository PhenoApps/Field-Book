"""
Feature: Observation Units (Plots)
  As a Field Book Android client
  I want to list observation units for a study

  Scenario: Returns empty list when no units seeded
    Given an empty database, When GET /brapi/v2/observationunits
    Then returns 200 with an empty data array

  Scenario: Returns units filtered by studyDbId
    Given units seeded for different studies
    When GET /brapi/v2/observationunits?studyDbId=S1 is called
    Then returns only units belonging to study S1

  Scenario: Unit contains position and germplasm info
    Given an observation unit is seeded with position JSON
    When GET /brapi/v2/observationunits is called
    Then each unit has observationUnitDbId, germplasmName, observationUnitPosition

  Scenario: Supports pagination
    Given multiple units seeded
    When GET /brapi/v2/observationunits?page=0&pageSize=1 is called
    Then returns one unit with correct pagination metadata
"""

import json
import pytest


@pytest.mark.asyncio
async def test_observation_units_empty_list(client):
    response = await client.get("/brapi/v2/observationunits")
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []


@pytest.mark.asyncio
async def test_observation_units_filtered_by_study(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s1 = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    s2 = Study(study_db_id="s2", study_name="S2", program_db_id="p1", trial_db_id="t1")
    db_session.add_all([p, t, s1, s2])
    await db_session.flush()
    db_session.add_all([
        ObservationUnit(observation_unit_db_id="u1", study_db_id="s1", observation_unit_name="Plot 1"),
        ObservationUnit(observation_unit_db_id="u2", study_db_id="s1", observation_unit_name="Plot 2"),
        ObservationUnit(observation_unit_db_id="u3", study_db_id="s2", observation_unit_name="Plot A"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/observationunits?studyDbId=s1")
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 2


@pytest.mark.asyncio
async def test_observation_units_has_position_and_germplasm(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    db_session.add_all([p, t, s])
    await db_session.flush()
    db_session.add(ObservationUnit(
        observation_unit_db_id="u1",
        study_db_id="s1",
        observation_unit_name="Plot 1",
        germplasm_name="WheatVarA",
        germplasm_db_id="g1",
        observation_unit_position=json.dumps({
            "observationLevel": {"levelName": "plot", "levelOrder": 0},
            "positionCoordinateX": "1",
            "positionCoordinateY": "2",
        }),
    ))
    await db_session.commit()

    response = await client.get("/brapi/v2/observationunits")
    assert response.status_code == 200
    unit = response.json()["result"]["data"][0]
    assert unit["observationUnitDbId"] == "u1"
    assert unit["observationUnitName"] == "Plot 1"
    assert unit["germplasmName"] == "WheatVarA"
    pos = unit["observationUnitPosition"]
    assert pos["observationLevel"]["levelName"] == "plot"
    assert pos["positionCoordinateX"] == "1"


@pytest.mark.asyncio
async def test_observation_units_pagination(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    db_session.add_all([p, t, s])
    await db_session.flush()
    for i in range(5):
        db_session.add(ObservationUnit(
            observation_unit_db_id=f"u{i}", study_db_id="s1", observation_unit_name=f"Plot {i}"
        ))
    await db_session.commit()

    response = await client.get("/brapi/v2/observationunits?page=0&pageSize=2")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalCount"] == 5
