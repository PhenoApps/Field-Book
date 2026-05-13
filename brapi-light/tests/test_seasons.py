"""
Feature: Seasons List
  As a Field Book Android client
  I want to list available seasons
  So that users can tag observations with the correct growing season
"""

import pytest


@pytest.mark.asyncio
async def test_seasons_empty_list(client):
    response = await client.get("/brapi/v2/seasons")
    assert response.status_code == 200
    assert response.json()["result"]["data"] == []


@pytest.mark.asyncio
async def test_seasons_returns_seeded_data(client, db_session):
    from brapi_light.models.core import Season
    db_session.add_all([
        Season(season_db_id="s1", season="2024 Spring", year="2024"),
        Season(season_db_id="s2", season="2024 Fall", year="2024"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/seasons")
    assert response.status_code == 200
    result = response.json()["result"]
    assert len(result["data"]) == 2
    assert result["data"][0]["seasonDbId"] is not None


@pytest.mark.asyncio
async def test_seasons_pagination(client, db_session):
    from brapi_light.models.core import Season
    for i in range(5):
        db_session.add(Season(season_db_id=f"s{i}", season=f"Season {i}", year="2024"))
    await db_session.commit()

    response = await client.get("/brapi/v2/seasons?page=0&pageSize=2")
    assert response.status_code == 200
    assert len(response.json()["result"]["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalCount"] == 5
