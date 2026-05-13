"""
Feature: Trials List
  As a Field Book Android client
  I want to list trials within a program
"""

import pytest


@pytest.mark.asyncio
async def test_trials_empty_list(client):
    response = await client.get("/brapi/v2/trials")
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []


@pytest.mark.asyncio
async def test_trials_filtered_by_program(client, db_session):
    from brapi_light.models.core import Program, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    p2 = Program(program_db_id="p2", program_name="Rice")
    db_session.add_all([p1, p2])
    await db_session.flush()
    db_session.add_all([
        Trial(trial_db_id="t1", trial_name="Wheat Trial 1", program_db_id="p1"),
        Trial(trial_db_id="t2", trial_name="Wheat Trial 2", program_db_id="p1"),
        Trial(trial_db_id="t3", trial_name="Rice Trial 1", program_db_id="p2"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/trials?programDbId=p1")
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 2
    assert all(t["programDbId"] == "p1" for t in data)


@pytest.mark.asyncio
async def test_trials_unfiltered(client, db_session):
    from brapi_light.models.core import Program, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    db_session.add(p1)
    await db_session.flush()
    db_session.add_all([
        Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1"),
        Trial(trial_db_id="t2", trial_name="T2", program_db_id="p1"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/trials")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2


@pytest.mark.asyncio
async def test_trials_pagination(client, db_session):
    from brapi_light.models.core import Program, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    db_session.add(p1)
    await db_session.flush()
    for i in range(5):
        db_session.add(Trial(trial_db_id=f"t{i}", trial_name=f"Trial {i}", program_db_id="p1"))
    await db_session.commit()

    response = await client.get("/brapi/v2/trials?page=0&pageSize=2")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalCount"] == 5


@pytest.mark.asyncio
async def test_trials_has_required_fields(client, db_session):
    from brapi_light.models.core import Program, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    db_session.add(p1)
    await db_session.flush()
    db_session.add(Trial(trial_db_id="t1", trial_name="My Trial", program_db_id="p1"))
    await db_session.commit()

    response = await client.get("/brapi/v2/trials")
    assert response.status_code == 200
    trial = response.json()["result"]["data"][0]
    assert "trialDbId" in trial
    assert "trialName" in trial
    assert "programDbId" in trial
