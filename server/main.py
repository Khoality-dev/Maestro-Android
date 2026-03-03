import asyncio
import logging
from typing import Optional

from fastapi import FastAPI, Query, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import yt_dlp

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Maestro Server", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

YDL_OPTS_BASE = {
    "quiet": True,
    "no_warnings": True,
    "no_color": True,
}


def _search_sync(query: str, limit: int) -> list[dict]:
    opts = {
        **YDL_OPTS_BASE,
        "extract_flat": True,
        "skip_download": True,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        result = ydl.extract_info(f"ytsearch{limit}:{query}", download=False)

    entries = result.get("entries", []) if result else []
    tracks = []
    for e in entries:
        if not e:
            continue
        thumbnails = e.get("thumbnails") or []
        tracks.append({
            "id": e.get("id", ""),
            "title": e.get("title", ""),
            "artist": e.get("channel") or e.get("uploader"),
            "duration": e.get("duration"),
            "thumbnail": thumbnails[-1]["url"] if thumbnails else None,
            "url": e.get("webpage_url") or e.get("url") or f"https://www.youtube.com/watch?v={e.get('id', '')}",
        })
    return tracks


def _extract_sync(video_id: str) -> dict:
    opts = {
        **YDL_OPTS_BASE,
        "format": "bestaudio[ext=m4a]/bestaudio/best",
        "skip_download": True,
    }
    url = f"https://www.youtube.com/watch?v={video_id}"
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)

    if not info or not info.get("url"):
        raise HTTPException(status_code=404, detail="Could not extract stream URL")

    return {
        "stream_url": info["url"],
        "duration": info.get("duration"),
        "title": info.get("title"),
        "artist": info.get("channel") or info.get("uploader"),
    }


@app.get("/health")
async def health():
    return {"status": "ok", "name": "Maestro Server", "version": "1.0.0"}


@app.get("/search")
async def search(q: str = Query(..., min_length=1), limit: int = Query(5, ge=1, le=20)):
    try:
        tracks = await asyncio.to_thread(_search_sync, q, limit)
        return {"results": tracks}
    except Exception as e:
        logger.error(f"Search failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/extract")
async def extract(id: str = Query(..., min_length=1)):
    try:
        data = await asyncio.to_thread(_extract_sync, id)
        return data
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Extract failed: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=29171)
