import json
import logging
import subprocess
import threading
import time

# ---------------------------------------------------------------------------
# Android compatibility safety patch:
# On Android, Python processes cannot spawn OS subprocesses via fork/exec.
# When yt-dlp checks for external JS runtimes (node, phantomjs) or ffmpeg,
# calling subprocess invokes _posixsubprocess which crashes ART with SIGSEGV.
# Patching Popen to raise FileNotFoundError allows yt-dlp to cleanly fall back
# to its internal pure-Python JS interpreter without crashing the app.
# ---------------------------------------------------------------------------
class _AndroidSafePopen:
    def __init__(self, *args, **kwargs):
        raise FileNotFoundError("External subprocess binaries are not available on Android")

subprocess.Popen = _AndroidSafePopen
subprocess.call = lambda *args, **kwargs: -1
subprocess.check_call = lambda *args, **kwargs: -1
subprocess.check_output = lambda *args, **kwargs: b""
subprocess.run = lambda *args, **kwargs: subprocess.CompletedProcess(args, 1, b"", b"Subprocess disabled on Android")

import yt_dlp
from ytmusicapi import YTMusic

logger = logging.getLogger("HarmixEngine")

# ---------------------------------------------------------------------------
# Stream URL cache
#
# Direct Google stream URLs typically stay valid for several hours.
# Caching them in-memory eliminates repeated extraction delays.
# ---------------------------------------------------------------------------
_CACHE = {}
_CACHE_LOCK = threading.Lock()
_CACHE_TTL_SECONDS = 4 * 60 * 60
_CACHE_MAX_ENTRIES = 200

_BASE_OPTS = {
    "quiet": True,
    "no_warnings": True,
    "noplaylist": True,
    "skip_download": True,
    "socket_timeout": 12,
    "retries": 2,
    "format": "bestaudio[ext=m4a]/bestaudio/best",
    "cachedir": False,
    "nocheckcertificate": True,
    "no_color": True,
}

# Multi-strategy client list:
# YouTube blocks standard desktop web / android_music clients from datacenter IPs
# with "Sign in to confirm you're not a bot".
# The iOS, Safari, mweb, and creator clients bypass this check.
_EXTRACTOR_STRATEGIES = [
    # Strategy 1: iOS client (highest success rate without PO-token/bot challenge)
    {
        "name": "ios",
        "args": {
            "youtube": {
                "player_client": ["ios"],
                "skip": ["hls", "dash", "translated_subs"],
            }
        },
    },
    # Strategy 2: Mobile Web & Safari (loosest bot detection)
    {
        "name": "web_safari",
        "args": {
            "youtube": {
                "player_client": ["web_safari", "mweb"],
                "skip": ["hls", "dash", "translated_subs"],
            }
        },
    },
    # Strategy 3: Android Creator / TV
    {
        "name": "android_creator_tv",
        "args": {
            "youtube": {
                "player_client": ["android_creator", "tv"],
                "skip": ["hls", "dash", "translated_subs"],
            }
        },
    },
    # Strategy 4: Android Music & Android
    {
        "name": "android_music",
        "args": {
            "youtube": {
                "player_client": ["android_music", "android"],
                "player_skip": ["configs", "webpage"],
                "skip": ["hls", "dash", "translated_subs"],
            }
        },
    },
    # Strategy 5: Standard Web
    {
        "name": "web_fallback",
        "args": {
            "youtube": {
                "player_client": ["web"],
            }
        },
    },
]


def _video_id_only(val: str) -> str:
    if "watch?v=" in val:
        return val.split("watch?v=")[-1].split("&")[0]
    if "youtu.be/" in val:
        return val.split("youtu.be/")[-1].split("?")[0]
    return val


def _watch_url(video_id: str) -> str:
    vid = _video_id_only(video_id)
    return f"https://www.youtube.com/watch?v={vid}"


def _cache_get(key: str):
    with _CACHE_LOCK:
        entry = _CACHE.get(key)
        if not entry:
            return None
        if entry["expiresAt"] <= time.time():
            _CACHE.pop(key, None)
            return None
        return entry["payload"]


def _cache_put(key: str, payload: str):
    with _CACHE_LOCK:
        if len(_CACHE) >= _CACHE_MAX_ENTRIES:
            oldest = min(_CACHE, key=lambda k: _CACHE[k]["expiresAt"])
            _CACHE.pop(oldest, None)
        _CACHE[key] = {"payload": payload, "expiresAt": time.time() + _CACHE_TTL_SECONDS}


def _pick_url(info: dict):
    direct_url = info.get("url")
    if direct_url:
        return direct_url

    candidates = info.get("requested_formats") or info.get("formats") or []
    audio_only = [
        f for f in candidates
        if f.get("acodec") not in (None, "none") and f.get("vcodec") in (None, "none")
    ]
    pool = audio_only or candidates
    if not pool:
        return None
    best = max(pool, key=lambda f: f.get("abr") or f.get("tbr") or 0)
    return best.get("url")


def _extract(url: str, extractor_args: dict):
    opts = dict(_BASE_OPTS)
    opts["extractor_args"] = extractor_args
    with yt_dlp.YoutubeDL(opts) as ydl:
        return ydl.extract_info(url, download=False)


def _try_ytmusic_fallback(video_id: str):
    """Fallback using ytmusicapi player/song endpoint."""
    try:
        vid = _video_id_only(video_id)
        ytm = YTMusic()
        song = ytm.get_song(vid)
        streaming = song.get("streamingData", {})
        formats = streaming.get("adaptiveFormats", []) or streaming.get("formats", [])
        for f in formats:
            url = f.get("url")
            if url and ("audio" in f.get("mimeType", "") or f.get("hasAudio")):
                return {
                    "url": url,
                    "durationSeconds": int(song.get("videoDetails", {}).get("lengthSeconds", 0)) or None,
                    "title": song.get("videoDetails", {}).get("title"),
                    "uploader": song.get("videoDetails", {}).get("author"),
                }
    except Exception as e:
        logger.warning(f"ytmusicapi fallback failed: {e}")
    return None


def get_audio_url(video_id: str) -> str:
    """Resolve a playable audio stream URL with multi-client bypass."""
    url = _watch_url(video_id)
    vid = _video_id_only(video_id)

    cached = _cache_get(url)
    if cached:
        return cached

    last_error = None
    for strategy in _EXTRACTOR_STRATEGIES:
        try:
            info = _extract(url, strategy["args"])
            direct_url = _pick_url(info)
            if direct_url:
                duration = info.get("duration")
                payload = json.dumps({
                    "url": direct_url,
                    "durationSeconds": int(duration) if duration else None,
                    "title": info.get("title"),
                    "uploader": info.get("uploader") or info.get("channel"),
                })
                _cache_put(url, payload)
                return payload
            last_error = ValueError(f"no usable audio format via {strategy['name']}")
        except Exception as error:
            last_error = error
            logger.warning(f"Strategy {strategy['name']} failed for {vid}: {error}")

    # Fallback to direct ytmusicapi extraction if yt-dlp clients all faced issues
    fallback_res = _try_ytmusic_fallback(vid)
    if fallback_res:
        payload = json.dumps(fallback_res)
        _cache_put(url, payload)
        return payload

    raise RuntimeError(f"Could not extract audio for {url}: {last_error}")


def prefetch_audio_url(video_id: str) -> str:
    """Warm the cache for an upcoming track without raising."""
    try:
        get_audio_url(video_id)
        return "ok"
    except Exception as error:
        return f"failed: {error}"


def clear_cache() -> str:
    with _CACHE_LOCK:
        _CACHE.clear()
    return "ok"


def search(query: str) -> str:
    opts = dict(_BASE_OPTS)
    opts["extractor_args"] = _EXTRACTOR_STRATEGIES[0]["args"]
    opts["extract_flat"] = True
    opts.pop("format", None)

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(f"ytsearch10:{query}", download=False)
        entries = info.get("entries") or []

        results = []
        for entry in entries:
            if not entry:
                continue

            video_id = entry.get("id", "")
            webpage_url = entry.get("url") or entry.get("webpage_url") or (
                f"https://www.youtube.com/watch?v={video_id}" if video_id else ""
            )
            if not webpage_url:
                continue

            thumbnail = entry.get("thumbnail")
            if not thumbnail:
                thumbnails = entry.get("thumbnails") or []
                if thumbnails:
                    thumbnail = thumbnails[-1].get("url")

            results.append({
                "title": entry.get("title", "Unknown title"),
                "url": webpage_url,
                "thumbnailUrl": thumbnail,
                "uploader": entry.get("uploader") or entry.get("channel") or "",
            })

        return json.dumps(results)
