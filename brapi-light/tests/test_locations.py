"""
Feature: Locations List
  As a Field Book Android client
  I want to list available field locations
"""

import pytest


@pytest.mark.asyncio
async def test_locations_empty_list(client):
    response = await client.get("/brapi/v2/locations")
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []


@pytest.mark.asyncio
async def test_locations_returns_seeded_data(client, db_session):
    from brapi_light.models.core import Location
    db_session.add_all([
        Location(location_db_id="l1", location_name="Field A", country="USA"),
        Location(location_db_id="l2", location_name="Field B"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/locations")
    assert response.status_code == 200
    result = response.json()["result"]
    assert len(result["data"]) == 2
    assert result["data"][0]["locationName"] is not None


@pytest.mark.asyncio
async def test_locations_pagination(client, db_session):
    from brapi_light.models.core import Location
    for i in range(5):
        db_session.add(Location(location_db_id=f"l{i}", location_name=f"Field {i}"))
    await db_session.commit()

    response = await client.get("/brapi/v2/locations?page=0&pageSize=2")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalPages"] == 3
