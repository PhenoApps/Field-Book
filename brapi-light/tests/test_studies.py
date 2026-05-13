"""
Feature: Studies List
  As a Field Book Android client
  I want to list studies and get study details
"""

import pytest


@pytest.mark.asyncio
async def test_studies_filtered_by_trial(client, db_session):
    from brapi_light.models.core import Program, Study, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    t1 = Trial(trial_db_id="t1", trial_name="Trial 1", program_db_id="p1")
    t2 = Trial(trial_db_id="t2", trial_name="Trial 2", program_db_id="p1")
    db_session.add_all([p1, t1, t2])
    await db_session.flush()
    db_session.add_all([
        Study(study_db_id="s1", study_name="Study 1", program_db_id="p1", trial_db_id="t1"),
        Study(study_db_id="s2", study_name="Study 2", program_db_id="p1", trial_db_id="t1"),
        Study(study_db_id="s3", study_name="Study 3", program_db_id="p1", trial_db_id="t2"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/studies?trialDbId=t1")
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 2


@pytest.mark.asyncio
async def test_studies_unfiltered(client, db_session):
    from brapi_light.models.core import Program, Study, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    t1 = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    db_session.add_all([p1, t1])
    await db_session.flush()
    db_session.add_all([
        Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1"),
        Study(study_db_id="s2", study_name="S2", program_db_id="p1", trial_db_id="t1"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/studies")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2


@pytest.mark.asyncio
async def test_studies_get_by_id(client, db_session):
    from brapi_light.models.core import Program, Study, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    t1 = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    db_session.add_all([p1, t1])
    await db_session.flush()
    db_session.add(Study(
        study_db_id="s1", study_name="My Study", common_crop_name="Wheat",
        program_db_id="p1", trial_db_id="t1",
    ))
    await db_session.commit()

    response = await client.get("/brapi/v2/studies/s1")
    assert response.status_code == 200
    result = response.json()["result"]
    assert result["studyName"] == "My Study"
    assert result["commonCropName"] == "Wheat"


@pytest.mark.asyncio
async def test_studies_get_unknown_id_returns_404(client):
    response = await client.get("/brapi/v2/studies/nonexistent")
    assert response.status_code == 404


@pytest.mark.asyncio
async def test_studies_pagination(client, db_session):
    from brapi_light.models.core import Program, Study, Trial

    p1 = Program(program_db_id="p1", program_name="Wheat")
    t1 = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    db_session.add_all([p1, t1])
    await db_session.flush()
    for i in range(5):
        db_session.add(Study(
            study_db_id=f"s{i}", study_name=f"Study {i}",
            program_db_id="p1", trial_db_id="t1",
        ))
    await db_session.commit()

    response = await client.get("/brapi/v2/studies?page=0&pageSize=2")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalCount"] == 5
