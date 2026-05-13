"""
Feature: Programs List
  As a Field Book Android client
  I want to list BrAPI programs
  So that users can select a breeding program when importing field data

  Scenario: Returns empty list when no programs seeded
    Given an empty database
    When GET /brapi/v2/programs is called
    Then returns 200 with an empty data array and correct pagination metadata

  Scenario: Returns all seeded programs
    Given the database has 2 programs seeded
    When GET /brapi/v2/programs is called
    Then returns 200 with both programs in data array and totalCount = 2

  Scenario: Supports pagination with page and pageSize
    Given the database has 5 programs seeded
    When GET /brapi/v2/programs?page=0&pageSize=2 is called
    Then returns first 2 programs with totalCount = 5, totalPages = 3

  Scenario: Each program has required BrAPI fields
    Given the database has programs seeded
    When GET /brapi/v2/programs is called
    Then each program includes programDbId and programName
"""

import pytest


@pytest.mark.asyncio
async def test_programs_empty_list(client):
    """GET /brapi/v2/programs returns empty data when no programs exist."""
    response = await client.get("/brapi/v2/programs")
    assert response.status_code == 200
    data = response.json()
    assert data["result"]["data"] == []
    assert data["metadata"]["pagination"]["totalCount"] == 0


@pytest.mark.asyncio
async def test_programs_returns_seeded_data(client, db_session):
    """GET /brapi/v2/programs returns all seeded programs."""
    from brapi_light.models.core import Program

    db_session.add_all([
        Program(program_db_id="p1", program_name="Wheat Breeding"),
        Program(program_db_id="p2", program_name="Rice Improvement"),
    ])
    await db_session.commit()

    response = await client.get("/brapi/v2/programs")
    assert response.status_code == 200
    result = response.json()["result"]
    assert len(result["data"]) == 2
    assert response.json()["metadata"]["pagination"]["totalCount"] == 2


@pytest.mark.asyncio
async def test_programs_pagination(client, db_session):
    """GET /brapi/v2/programs supports page and pageSize query params."""
    from brapi_light.models.core import Program

    for i in range(5):
        db_session.add(Program(program_db_id=f"p{i}", program_name=f"Program {i}"))
    await db_session.commit()

    response = await client.get("/brapi/v2/programs?page=0&pageSize=2")
    assert response.status_code == 200
    result = response.json()["result"]
    assert len(result["data"]) == 2
    pagination = response.json()["metadata"]["pagination"]
    assert pagination["totalCount"] == 5
    assert pagination["totalPages"] == 3
    assert pagination["currentPage"] == 0


@pytest.mark.asyncio
async def test_programs_has_required_fields(client, db_session):
    """Each program in response includes programDbId and programName."""
    from brapi_light.models.core import Program

    db_session.add(Program(program_db_id="p1", program_name="Wheat"))
    await db_session.commit()

    response = await client.get("/brapi/v2/programs")
    assert response.status_code == 200
    program = response.json()["result"]["data"][0]
    assert "programDbId" in program
    assert "programName" in program
