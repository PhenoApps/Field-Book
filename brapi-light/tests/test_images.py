"""
Feature: Images
  As a Field Book Android client
  I want to upload image metadata and content
  So that field photos sync to the server

  Scenario: POST creates image metadata record
    Given a valid image metadata payload
    When POST /brapi/v2/images is called
    Then returns the created image with server-generated imageDbId

  Scenario: PUT uploads image content
    Given an image metadata record exists
    When PUT /brapi/v2/images/{id}/imagecontent with binary data
    Then stores the content and returns the updated image

  Scenario: PUT returns 404 for unknown image
    Given no image with the given ID
    When PUT /brapi/v2/images/{id}/imagecontent
    Then returns 404
"""

import pytest


@pytest.mark.asyncio
async def test_images_post_metadata(client, db_session):
    from brapi_light.models.core import Program, Study, Trial
    from brapi_light.models.phenotyping import ObservationUnit

    p = Program(program_db_id="p1", program_name="Wheat")
    t = Trial(trial_db_id="t1", trial_name="T1", program_db_id="p1")
    s = Study(study_db_id="s1", study_name="S1", program_db_id="p1", trial_db_id="t1")
    u = ObservationUnit(observation_unit_db_id="u1", study_db_id="s1")
    db_session.add_all([p, t, s, u])
    await db_session.commit()

    response = await client.post("/brapi/v2/images", json=[{
        "observationUnitDbId": "u1",
        "imageFileName": "photo.jpg",
        "imageName": "Plot Photo",
        "mimeType": "image/jpeg",
        "imageFileSize": 1024,
    }])
    assert response.status_code == 200
    data = response.json()["result"]["data"]
    assert len(data) == 1
    assert data[0]["imageDbId"] is not None
    assert data[0]["imageFileName"] == "photo.jpg"


@pytest.mark.asyncio
async def test_images_put_content(client, db_session):
    from brapi_light.models.phenotyping import Image

    img = Image(image_db_id="img1", image_file_name="test.jpg", mime_type="image/jpeg")
    db_session.add(img)
    await db_session.commit()

    response = await client.put(
        "/brapi/v2/images/img1/imagecontent",
        content=b"\x89PNG fake image data",
        headers={"Content-Type": "application/octet-stream"},
    )
    assert response.status_code == 200
    assert response.json()["result"]["imageDbId"] == "img1"


@pytest.mark.asyncio
async def test_images_put_content_404(client):
    response = await client.put(
        "/brapi/v2/images/nonexistent/imagecontent",
        content=b"data",
    )
    assert response.status_code == 404
