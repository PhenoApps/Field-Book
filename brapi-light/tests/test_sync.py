"""
Feature: Optimistic Locking & Incremental Sync
  As a Field Book Android client
  I want to sync only changed observations and detect write conflicts
"""

import pytest


@pytest.mark.asyncio
async def test_observation_post_has_rev1(client, db_session):
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
        "studyDbId": "s1",
        "observationVariableName": "Height",
        "value": "42",
    }])
    assert response.status_code == 200
    obs = response.json()["result"]["data"][0]
    assert obs["rev"] == 1
    assert obs["lastSyncedTime"] is not None


@pytest.mark.asyncio
async def test_observation_put_matching_rev_updates(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    o = Observation(observation_db_id="o1", observation_unit_db_id="u1",
                    observation_variable_db_id="v1", study_db_id="s1",
                    observation_variable_name="Height", value="old", rev=1)
    db_session.add_all([p, t, s, u, v, o])
    await db_session.commit()

    response = await client.put("/brapi/v2/observations", json={
        "o1": {"value": "new", "rev": 1},
    })
    assert response.status_code == 200
    obs = response.json()["result"]["data"][0]
    assert obs["value"] == "new"
    assert obs["rev"] == 2


@pytest.mark.asyncio
async def test_observation_put_stale_rev_returns_409(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    o = Observation(observation_db_id="o1", observation_unit_db_id="u1",
                    observation_variable_db_id="v1", study_db_id="s1",
                    observation_variable_name="Height", value="current", rev=2)
    db_session.add_all([p, t, s, u, v, o])
    await db_session.commit()

    response = await client.put("/brapi/v2/observations", json={
        "o1": {"value": "stale_update", "rev": 1},
    })
    assert response.status_code == 200
    meta = response.json()["metadata"]
    assert len(meta["status"]) > 0
    assert "conflicts" in meta["status"][0]
    conflicted_ids = [c["observationDbId"] for c in meta["status"][0]["conflicts"]]
    assert "o1" in conflicted_ids
    # value should NOT have changed
    assert response.json()["result"]["data"] == []


@pytest.mark.asyncio
async def test_sync_changes_returns_only_modified_since(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    db_session.add_all([p, t, s, u, v])
    await db_session.flush()
    db_session.add_all([
        Observation(observation_db_id="o1", observation_unit_db_id="u1",
                    observation_variable_db_id="v1", study_db_id="s1",
                    observation_variable_name="Height", value="old",
                    last_synced_time="2024-01-01T00:00:00Z"),
        Observation(observation_db_id="o2", observation_unit_db_id="u1",
                    observation_variable_db_id="v1", study_db_id="s1",
                    observation_variable_name="Height", value="new",
                    last_synced_time="2024-06-15T00:00:00Z"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/sync/changes?since=2024-06-01T00:00:00Z")
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 1
    assert data[0]["value"] == "new"


@pytest.mark.asyncio
async def test_sync_changes_pagination(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    db_session.add_all([p, t, s, u, v])
    await db_session.flush()
    for i in range(5):
        db_session.add(Observation(
            observation_db_id=f"o{i}", observation_unit_db_id="u1",
            observation_variable_db_id="v1", study_db_id="s1",
            observation_variable_name="Height", value=str(i),
            last_synced_time="2024-06-15T00:00:00Z",
        ))
    await db_session.commit()

    response = await client.get("/brapi/v2/sync/changes?since=2024-01-01T00:00:00Z&page=0&pageSize=2")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalCount"] == 5


@pytest.mark.asyncio
async def test_sync_changes_empty_when_no_changes(client, db_session):
    response = await client.get("/brapi/v2/sync/changes?since=2099-01-01T00:00:00Z")
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []
