"""
Feature: Observations CRUD
  As a Field Book Android client
  I want to create, read, and update observations
  So that collected phenotype data syncs to/from the server

  Scenario: GET returns observations filtered by studyDbId
    Given observations exist in a study
    When GET /brapi/v2/observations?studyDbId=S1
    Then returns observations for study S1 only

  Scenario: GET returns empty list for no matching observations
    Given no observations in a study
    When GET /brapi/v2/observations?studyDbId=S1
    Then returns empty data array

  Scenario: POST creates a new observation with required fields
    Given a study with units and variables
    When POST /brapi/v2/observations with unitDbId, variableDbId, value
    Then returns the created observation with server-generated observationDbId

  Scenario: POST creates multiple observations in one request
    Given a study with units and variables
    When POST /brapi/v2/observations with an array of observations
    Then returns all created observations with generated IDs

  Scenario: PUT updates existing observation value
    Given an observation exists
    When PUT /brapi/v2/observations with updated value
    Then returns the updated observation

  Scenario: PUT handles non-existent observation
    Given no observation with given DbId
    When PUT /brapi/v2/observations with nonexistent ID
    Then no error, observation is silently skipped
"""

import pytest


@pytest.mark.asyncio
async def test_observations_get_by_study(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s1 = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    s2 = Study(study_db_id="s2", study_name="S2", program_db_id="p1", trial_db_id="t1")
    db_session.add_all([p, t, s1, s2])
    await db_session.flush()
    u1 = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    u2 = ObservationUnit(observation_unit_db_id="u2", study_db_id="s2")
    v1 = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    db_session.add_all([u1, u2, v1])
    await db_session.flush()
    db_session.add_all([
        Observation(observation_db_id="o1", observation_unit_db_id="u1",
                    observation_variable_db_id="v1", study_db_id="s1",
                    observation_variable_name="Height", value="150"),
        Observation(observation_db_id="o2", observation_unit_db_id="u2",
                    observation_variable_db_id="v1", study_db_id="s2",
                    observation_variable_name="Height", value="160"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/observations?studyDbId=s1")
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 1
    assert data[0]["value"] == "150"


@pytest.mark.asyncio
async def test_observations_get_empty(client):
    response = await client.get("/brapi/v2/observations?studyDbId=nonexistent")
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []


@pytest.mark.asyncio
async def test_observations_post_creates(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    db_session.add_all([p, t, s, u, v])
    await db_session.commit()

    response = await client.post("/brapi/v2/observations", json=[{
        "observationUnitDbId": "u1",
        "observationVariableDbId": "v1",
        "value": "42",
        "studyDbId": "s1",
        "observationVariableName": "Height",
    }])
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 1
    assert data[0]["observationDbId"] is not None
    assert data[0]["value"] == "42"


@pytest.mark.asyncio
async def test_observations_post_multiple(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    db_session.add_all([p, t, s, u, v])
    await db_session.commit()

    response = await client.post("/brapi/v2/observations", json=[
        {"observationUnitDbId": "u1", "observationVariableDbId": "v1", "studyDbId": "s1",
         "observationVariableName": "Height", "value": "10"},
        {"observationUnitDbId": "u1", "observationVariableDbId": "v1", "studyDbId": "s1",
         "observationVariableName": "Height", "value": "20"},
    ])
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2


@pytest.mark.asyncio
async def test_observations_put_updates(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    o = Observation(observation_db_id="o1", observation_unit_db_id="u1",
                    observation_variable_db_id="v1", study_db_id="s1",
                    observation_variable_name="Height", value="old")
    db_session.add_all([p, t, s, u, v, o])
    await db_session.commit()

    response = await client.put("/brapi/v2/observations", json={
        "o1": {"value": "new"},
    })
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 1
    assert data[0]["value"] == "new"


@pytest.mark.asyncio
async def test_observations_put_nonexistent(client, db_session):
    response = await client.put("/brapi/v2/observations", json={
        "nonexistent": {"value": "something"},
    })
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []
