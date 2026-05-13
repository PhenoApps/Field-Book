"""
Feature: Multi-User Collaboration
  As a team of field researchers
  I want multiple users to collect and sync data without losing work
  So that our collaborative phenotyping yields complete datasets

  Scenario: Two users create different observations, both sync successfully
    Given User A posts observations for plot 1
    And User B posts observations for plot 2
    When both sets of observations are uploaded
    Then all observations are available via GET

  Scenario: Two users edit the same observation — conflict detected
    Given User A edits observation value to "42"
    And the server is now at rev=2
    When User B tries to edit with stale rev=1
    Then the server returns conflict status
    And User B can fetch latest via sync/changes

  Scenario: Incremental sync returns only new changes
    Given observations created at different times
    When User B calls sync/changes with a timestamp after User A's sync
    Then only observations created after that timestamp are returned

  Scenario: Full sync flow — post, edit, conflict, resolve
    Given a complete study setup
    When two users perform a full sync cycle (post → edit → conflict → resolve)
    Then the final data set is consistent and complete
"""

import pytest


@pytest.mark.asyncio
async def test_two_users_create_different_observations(client, db_session):
    """User A and User B each create observations for different plots."""
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit, ObservationVariable

    # Setup: program, trial, study, units, variable
    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u1 = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1", observation_unit_name="Plot 1")
    u2 = ObservationUnit(observation_unit_db_id="u2", study_db_id="s1", observation_unit_name="Plot 2")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    db_session.add_all([p, t, s, u1, u2, v])
    await db_session.commit()

    # User A posts observation for plot 1
    resp_a = await client.post("/brapi/v2/observations", json=[{
        "observationUnitDbId": "u1",
        "observationVariableDbId": "v1",
        "studyDbId": "s1",
        "observationVariableName": "Height",
        "value": "150",
        "collector": "UserA",
    }])
    assert resp_a.status_code == 200
    obs_a = resp_a.json()["result"]["data"][0]
    assert obs_a["rev"] == 1
    assert obs_a["observationDbId"] is not None

    # User B posts observation for plot 2
    resp_b = await client.post("/brapi/v2/observations", json=[{
        "observationUnitDbId": "u2",
        "observationVariableDbId": "v1",
        "studyDbId": "s1",
        "observationVariableName": "Height",
        "value": "160",
        "collector": "UserB",
    }])
    assert resp_b.status_code == 200
    obs_b = resp_b.json()["result"]["data"][0]
    assert obs_b["rev"] == 1

    # Both observations are visible
    resp = await client.get("/brapi/v2/observations?studyDbId=s1")
    assert resp.status_code == 200
    data = resp.json()["result"]["data"]
    assert len(data) == 2
    values = {o["value"] for o in data}
    assert values == {"150", "160"}


@pytest.mark.asyncio
async def test_concurrent_edit_conflict_detected(client, db_session):
    """User B tries updating with stale rev after User A already updated."""
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    o = Observation(observation_db_id="o1", observation_unit_db_id="u1",
                    observation_variable_db_id="v1", study_db_id="s1",
                    observation_variable_name="Height", value="100", rev=1)
    db_session.add_all([p, t, s, u, v, o])
    await db_session.commit()

    # User A edits observation (rev=1 → rev=2)
    resp_a = await client.put("/brapi/v2/observations", json={
        "o1": {"value": "42", "rev": 1},
    })
    assert resp_a.status_code == 200
    assert resp_a.json()["result"]["data"][0]["rev"] == 2

    # User B tries editing with stale rev=1 → conflict
    resp_b = await client.put("/brapi/v2/observations", json={
        "o1": {"value": "99", "rev": 1},
    })
    assert resp_b.status_code == 200
    meta = resp_b.json()["metadata"]
    assert len(meta["status"]) > 0
    assert "conflicts" in meta["status"][0]
    assert resp_b.json()["result"]["data"] == []

    # GET still shows User A's value (not overwritten by User B)
    resp = await client.get("/brapi/v2/observations?studyDbId=s1")
    assert resp.json()["result"]["data"][0]["value"] == "42"


@pytest.mark.asyncio
async def test_incremental_sync_timestamp_filter(client, db_session):
    """sync/changes only returns observations modified after the given timestamp."""
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import Observation, ObservationUnit, ObservationVariable

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    v = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    db_session.add_all([p, t, s, u, v])
    await db_session.flush()

    # Old observation
    db_session.add(Observation(
        observation_db_id="o1", observation_unit_db_id="u1",
        observation_variable_db_id="v1", study_db_id="s1",
        observation_variable_name="Height", value="old",
        last_synced_time="2024-01-01T00:00:00Z",
    ))
    # New observation
    db_session.add(Observation(
        observation_db_id="o2", observation_unit_db_id="u1",
        observation_variable_db_id="v1", study_db_id="s1",
        observation_variable_name="Height", value="new",
        last_synced_time="2024-06-15T00:00:00Z",
    ))
    await db_session.commit()

    # User B syncs with timestamp after User A's last sync
    resp = await client.get("/brapi/v2/sync/changes?since=2024-03-01T00:00:00Z")
    assert resp.status_code == 200
    data = resp.json()["result"]["data"]
    assert len(data) == 1
    assert data[0]["value"] == "new"


@pytest.mark.asyncio
async def test_full_sync_cycle(client, db_session):
    """Complete collaboration cycle: post → edit → conflict → resolve."""
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit, ObservationVariable

    # ── Setup ──
    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u1 = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    u2 = ObservationUnit(observation_unit_db_id="u2", study_db_id="s1")
    v1 = ObservationVariable(observation_variable_db_id="v1", observation_variable_name="Height")
    v2 = ObservationVariable(observation_variable_db_id="v2", observation_variable_name="Weight")
    db_session.add_all([p, t, s, u1, u2, v1, v2])
    await db_session.commit()

    # ── Step 1: User A posts observations offline ──
    resp = await client.post("/brapi/v2/observations", json=[
        {"observationUnitDbId": "u1", "observationVariableDbId": "v1",
         "studyDbId": "s1", "observationVariableName": "Height", "value": "150"},
        {"observationUnitDbId": "u1", "observationVariableDbId": "v2",
         "studyDbId": "s1", "observationVariableName": "Weight", "value": "2.5"},
    ])
    assert resp.status_code == 200
    obs_a1_id = resp.json()["result"]["data"][0]["observationDbId"]

    # ── Step 2: User B posts observation for different plot ──
    resp = await client.post("/brapi/v2/observations", json=[{
        "observationUnitDbId": "u2", "observationVariableDbId": "v1",
        "studyDbId": "s1", "observationVariableName": "Height", "value": "180",
    }])
    assert resp.status_code == 200

    # ── Step 3: Verify all 3 observations exist ──
    resp = await client.get("/brapi/v2/observations?studyDbId=s1")
    assert len(resp.json()["result"]["data"]) == 3

    # ── Step 4: User A edits observation (rev=1 → rev=2) ──
    resp = await client.put("/brapi/v2/observations", json={
        obs_a1_id: {"value": "155", "rev": 1},
    })
    assert resp.status_code == 200
    assert resp.json()["result"]["data"][0]["rev"] == 2
    assert resp.json()["result"]["data"][0]["value"] == "155"

    # ── Step 5: User B tries stale edit (conflict) ──
    resp = await client.put("/brapi/v2/observations", json={
        obs_a1_id: {"value": "999", "rev": 1},
    })
    assert resp.status_code == 200
    has_conflict = any(
        "conflicts" in s for s in resp.json()["metadata"]["status"]
    )
    assert has_conflict, "Expected conflict for stale rev"

    # ── Step 6: User B fetches latest → sees User A's value ──
    resp = await client.get("/brapi/v2/observations?studyDbId=s1")
    data = resp.json()["result"]["data"]
    obs_a1 = next(o for o in data if o["observationDbId"] == obs_a1_id)
    assert obs_a1["value"] == "155"  # User A's edit preserved
    assert obs_a1["rev"] == 2

    # ── Step 7: User B edits with correct rev ──
    resp = await client.put("/brapi/v2/observations", json={
        obs_a1_id: {"value": "160", "rev": 2},
    })
    assert resp.status_code == 200
    assert resp.json()["result"]["data"][0]["rev"] == 3
