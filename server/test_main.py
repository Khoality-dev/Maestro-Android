import sys
from pathlib import Path
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).parent))
import main  # noqa: E402


class FakeYDL:
    """Stub yt-dlp YoutubeDL context manager that returns a fresh URL each call."""

    counter = 0

    def __init__(self, opts):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def extract_info(self, url, download=False):
        FakeYDL.counter += 1
        return {
            "url": f"https://cdn/stream-{FakeYDL.counter}",
            "duration": 60,
            "title": "Song",
            "channel": "Artist",
        }


@pytest.fixture
def client():
    main._extract_cache.clear()
    main._search_cache.clear()
    FakeYDL.counter = 0
    with patch.object(main.yt_dlp, "YoutubeDL", FakeYDL):
        yield TestClient(main.app)


def test_extract_serves_cache_on_repeat_calls(client):
    r1 = client.get("/extract", params={"id": "vid1"})
    r2 = client.get("/extract", params={"id": "vid1"})

    assert r1.status_code == 200
    assert r2.status_code == 200
    assert r1.json()["stream_url"] == "https://cdn/stream-1"
    # second call served from cache → same URL, yt-dlp not invoked again
    assert r2.json()["stream_url"] == "https://cdn/stream-1"
    assert FakeYDL.counter == 1


def test_extract_refresh_bypasses_cache(client):
    r1 = client.get("/extract", params={"id": "vid1"})
    r2 = client.get("/extract", params={"id": "vid1", "refresh": "true"})
    r3 = client.get("/extract", params={"id": "vid1"})

    assert r1.json()["stream_url"] == "https://cdn/stream-1"
    # refresh forced a fresh extract
    assert r2.json()["stream_url"] == "https://cdn/stream-2"
    # cache is now repopulated with the fresh result
    assert r3.json()["stream_url"] == "https://cdn/stream-2"
    assert FakeYDL.counter == 2


def test_extract_returns_404_when_yt_dlp_has_no_url():
    class NoUrlYDL(FakeYDL):
        def extract_info(self, url, download=False):
            return {"id": "x"}  # no "url" key

    main._extract_cache.clear()
    with patch.object(main.yt_dlp, "YoutubeDL", NoUrlYDL):
        client = TestClient(main.app)
        r = client.get("/extract", params={"id": "vid1"})

    assert r.status_code == 404


def test_extract_response_includes_metadata(client):
    r = client.get("/extract", params={"id": "vid1"})
    body = r.json()
    assert body["stream_url"] == "https://cdn/stream-1"
    assert body["duration"] == 60
    assert body["title"] == "Song"
    assert body["artist"] == "Artist"
