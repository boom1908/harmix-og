import json
import re
import requests
from ytmusicapi import YTMusic

_ytmusic = YTMusic()
_GLOBAL_TOP_SONGS_PLAYLIST_ID = "PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i"

def _duration_seconds(entry):
    if entry.get("duration_seconds") is not None:
        return entry.get("duration_seconds")
    raw = entry.get("duration") or entry.get("length")
    if not raw or not isinstance(raw, str) or ":" not in raw:
        return None
    try:
        parts = [int(p) for p in raw.split(":")]
        seconds = 0
        for part in parts:
            seconds = seconds * 60 + part
        return seconds
    except (ValueError, TypeError):
        return None

def _thumbnail_url(thumbnails):
    if not thumbnails:
        return None
    url = thumbnails[-1]["url"]
    high_res_url = re.sub(r"=w\d+-h\d+.*$", "=w1080-h1080-l90-rj", url)
    return high_res_url

def _artist_name(artists):
    return artists[0]["name"] if artists and artists[0].get("name") else ""

def get_up_next(video_id: str, limit: int = 10) -> str:
    result = _ytmusic.get_watch_playlist(videoId=video_id, limit=limit + 5)
    tracks = result.get("tracks", [])

    up_next = []
    for track in tracks:
        track_video_id = track.get("videoId")
        if not track_video_id or track_video_id == video_id:
            continue
        up_next.append({
            "videoId": track_video_id,
            "title": track.get("title", "Unknown title"),
            "artist": _artist_name(track.get("artists")),
            "thumbnailUrl": _thumbnail_url(track.get("thumbnail") or []),
            "durationSeconds": _duration_seconds(track),
        })
        if len(up_next) >= limit:
            break
    return json.dumps(up_next)

def _entry_to_item(entry):
    video_id = entry.get("videoId")
    if not video_id:
        return None
    return {
        "videoId": video_id,
        "title": entry.get("title", "Unknown title"),
        "artist": _artist_name(entry.get("artists")) or (entry.get("author") or ""),
        "thumbnailUrl": _thumbnail_url(entry.get("thumbnails") or entry.get("thumbnail") or []),
        "durationSeconds": _duration_seconds(entry),
    }

def _normalized_search_text(value):
    # Keep Unicode letters so Hindi and other non-Latin searches are ranked
    # instead of collapsing to an empty query.
    return re.sub(r"[^\w]+", " ", (value or "").lower(), flags=re.UNICODE).strip()

def _score(item, query, position, source_bonus):
    """Rank the way a person would expect from typing the same words on YouTube."""
    q = _normalized_search_text(query)
    tokens = [t for t in q.split() if t]
    title = _normalized_search_text(item["title"])
    artist = _normalized_search_text(item["artist"])
    haystack = (title + " " + artist).strip()

    score = source_bonus
    if q and q in title:
        score += 70
    elif q and q in haystack:
        score += 45
    if q and title.startswith(q):
        score += 30
    title_matches = sum(1 for t in tokens if t in title)
    matched = sum(1 for t in tokens if t in haystack)
    score += 18 * title_matches
    if tokens:
        score += 35 * (matched / len(tokens))
        if matched == len(tokens):
            score += 18
    # Prefer real tracks over hour-long mixes / 20-second clips.
    duration = item.get("durationSeconds") or 0
    if 60 <= duration <= 900:
        score += 12
    elif duration > 2400:
        score -= 20
    # Keep each provider's own ordering as a tiebreaker.
    score -= position * 0.35
    return score

def search_songs(query: str, limit: int = 20) -> str:
    """
    Search songs, videos and the "top result" shelf together, then merge and
    re-rank so the list matches what the same keywords return on YouTube.
    """
    limit = max(1, min(int(limit), 50))
    fetch_limit = min(max(limit * 2, 25), 50)
    buckets = []
    for filter_name, bonus in (("songs", 22), ("videos", 8), (None, 14)):
        try:
            if filter_name:
                results = _ytmusic.search(query, filter=filter_name, limit=fetch_limit)
            else:
                results = _ytmusic.search(query, limit=fetch_limit)
        except Exception:
            continue
        buckets.append((results or [], bonus))

    merged = {}
    for results, bonus in buckets:
        for position, entry in enumerate(results):
            if entry.get("resultType") in ("artist", "album", "playlist"):
                continue
            item = _entry_to_item(entry)
            if not item:
                continue
            score = _score(item, query, position, bonus)
            existing = merged.get(item["videoId"])
            if existing is None:
                item["_score"] = score
                merged[item["videoId"]] = item
            else:
                # Appearing in several shelves is itself a relevance signal.
                existing["_score"] = max(existing["_score"], score) + 10
                if not existing.get("durationSeconds"):
                    existing["durationSeconds"] = item.get("durationSeconds")
                if not existing.get("artist"):
                    existing["artist"] = item.get("artist")

    ranked = sorted(merged.values(), key=lambda i: i["_score"], reverse=True)[:limit]
    for item in ranked:
        item.pop("_score", None)
    return json.dumps(ranked)

def get_trending(limit: int = 15) -> str:
    items = []
    try:
        charts = _ytmusic.get_charts(country="ZZ")
        videos_section = charts.get("videos") or charts.get("trending") or {}
        raw_list = videos_section.get("items") if isinstance(videos_section, dict) else videos_section
        raw_list = raw_list or []

        for entry in raw_list:
            video_id = entry.get("videoId")
            if not video_id:
                continue
            items.append({
                "videoId": video_id,
                "title": entry.get("title", "Unknown title"),
                "artist": _artist_name(entry.get("artists")),
                "thumbnailUrl": _thumbnail_url(entry.get("thumbnails") or []),
                "durationSeconds": _duration_seconds(entry),
            })
    except Exception as exc:
        print(f"[metadata_engine] global YouTube Music charts failed: {exc}", flush=True)
        items = []

    if not items:
        try:
            playlist = _ytmusic.get_playlist(_GLOBAL_TOP_SONGS_PLAYLIST_ID, limit=limit)
            for entry in playlist.get("tracks", []):
                item = _entry_to_item(entry)
                if item:
                    items.append(item)
        except Exception as exc:
            print(f"[metadata_engine] global top-songs playlist fallback failed: {exc}", flush=True)
    return json.dumps(items[:limit])

def get_for_you(artists_json: str, limit: int = 12) -> str:
    try:
        artists = json.loads(artists_json)
    except (TypeError, json.JSONDecodeError):
        artists = []

    items = []
    seen = set()
    per_artist = max(4, (limit + max(len(artists), 1) - 1) // max(len(artists), 1))
    for artist in artists[:5]:
        try:
            results = _ytmusic.search(f"{artist} songs", filter="songs", limit=per_artist)
        except Exception as exc:
            print(f"[metadata_engine] personalized search failed for {artist!r}: {exc}", flush=True)
            continue
        for entry in results:
            item = _entry_to_item(entry)
            if not item or item["videoId"] in seen:
                continue
            seen.add(item["videoId"])
            items.append(item)
            if len(items) >= limit:
                return json.dumps(items)
    return json.dumps(items)

def get_lyrics(title: str, artist: str, duration_seconds: int = 0) -> str:
    clean_artist = re.sub(r"\s*-\s*Topic$", "", artist).strip()
    params = {
        "track_name": title,
        "artist_name": clean_artist,
    }
    if duration_seconds > 0:
        params["duration"] = duration_seconds

    try:
        response = requests.get(
            "https://lrclib.net/api/get",
            params=params,
            headers={"User-Agent": "Harmix/0.1 (Android app)"},
            timeout=8,
        )
        if response.status_code != 200:
            return json.dumps({"syncedLyrics": None, "plainLyrics": None, "found": False})

        data = response.json()
        return json.dumps({
            "syncedLyrics": data.get("syncedLyrics"),
            "plainLyrics": data.get("plainLyrics"),
            "found": True,
        })
    except Exception:
        return json.dumps({"syncedLyrics": None, "plainLyrics": None, "found": False})
