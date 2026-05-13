"""
Feature: Server Info Discovery
  As a Field Book Android client
  I want to discover the BrAPI server capabilities
  So that I can verify compatibility before syncing data

  Scenario: Returns server metadata
    Given a running brapi-light server
    When GET /brapi/v2/serverinfo is called
    Then returns 200 with serverName, serverVersion, and BrAPI calls list

  Scenario: Each call lists supported HTTP methods
    Given a running brapi-light server
    When GET /brapi/v2/serverinfo is called
    Then each entry in calls[] lists at least one HTTP method in methods[]

  Scenario: Lists all implemented endpoints
    Given a running brapi-light server
    When GET /brapi/v2/serverinfo is called
    Then calls[] includes studies, trials, programs, observations, observationunits,
         variables, seasons, locations, people, images, serverinfo
"""

import pytest


@pytest.mark.asyncio
async def test_serverinfo_returns_server_metadata(client):
    """GET /brapi/v2/serverinfo returns 200 with server name, version, and BrAPI calls."""
    response = await client.get("/brapi/v2/serverinfo")
    assert response.status_code == 200
    data = response.json()
    assert "result" in data
    result = data["result"]
    assert "serverName" in result
    assert "serverVersion" in result
    assert "calls" in result


@pytest.mark.asyncio
async def test_serverinfo_calls_have_methods(client):
    """Each endpoint call in serverinfo lists supported HTTP methods."""
    response = await client.get("/brapi/v2/serverinfo")
    assert response.status_code == 200
    calls = response.json()["result"]["calls"]
    for call in calls:
        assert "methods" in call
        assert len(call["methods"]) > 0


EXPECTED_SERVICES = {
    "serverinfo", "programs", "trials", "studies", "observationunits",
    "variables", "observations", "seasons", "locations", "people", "images",
}


@pytest.mark.asyncio
async def test_serverinfo_lists_all_implemented_endpoints(client):
    """Server info reports all 11 endpoint groups we implement."""
    response = await client.get("/brapi/v2/serverinfo")
    assert response.status_code == 200
    calls = response.json()["result"]["calls"]
    services_found = {c["service"] for c in calls}
    missing = EXPECTED_SERVICES - services_found
    assert missing == set(), f"Missing services: {missing}"
