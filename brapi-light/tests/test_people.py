"""
Feature: People List
  As a Field Book Android client
  I want to list registered people
"""

import pytest


@pytest.mark.asyncio
async def test_people_empty_list(client):
    response = await client.get("/brapi/v2/people")
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []


@pytest.mark.asyncio
async def test_people_returns_seeded_data(client, db_session):
    from brapi_light.models.core import Person
    db_session.add_all([
        Person(person_db_id="u1", first_name="Jane", last_name="Doe"),
        Person(person_db_id="u2", first_name="John", last_name="Smith"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/people")
    assert response.status_code == 200
    result = response.json()["result"]
    assert len(result["data"]) == 2
    assert "firstName" in result["data"][0]


@pytest.mark.asyncio
async def test_people_pagination(client, db_session):
    from brapi_light.models.core import Person
    for i in range(5):
        db_session.add(Person(person_db_id=f"u{i}", first_name=f"User{i}"))
    await db_session.commit()

    response = await client.get("/brapi/v2/people?page=0&pageSize=2")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalCount"] == 5
