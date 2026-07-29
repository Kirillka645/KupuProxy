#!/usr/bin/env python3
"""Mirror public proxy feeds with size limits and basic MTProto validation."""

from __future__ import annotations

import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "proxy-feeds"
MAX_BYTES = 8 * 1024 * 1024
MAX_LINES = 100_000
USER_AGENT = "KupuProxy-feed-mirror/1.0"

FEEDS = {
    "kort_ru.txt": "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_ru.txt",
    "kort_eu.txt": "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_eu.txt",
    "kort_all.txt": "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
    "kort_verified.json": "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/verified/proxy_all_verified.json",
    "kort_stats.json": "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/verified/proxy_stats_verified.json",
    "kort_socks5.txt": "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/socks5.txt",
    "shablin_valid.txt": "https://raw.githubusercontent.com/shablin/mtproto-proxy/main/data/valid_proxy.txt",
    "shablin_valid.json": "https://raw.githubusercontent.com/shablin/mtproto-proxy/main/data/valid_proxy.json",
    "aliilapro_mtproto.txt": "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt",
    "hookzof_socks5.txt": "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt",
    "dubblebyte_all.txt": "https://raw.githubusercontent.com/dubblebyte/free-mtproto-proxies/main/all_proxies.txt",
    "dubblebyte_proxies.json": "https://raw.githubusercontent.com/dubblebyte/free-mtproto-proxies/main/proxies.json",
}

PROXY_URL = re.compile(r"(?:tg://proxy\?|https?://t\.me/proxy\?)[^\s\"'<>]+", re.IGNORECASE)
SECRET = re.compile(r"^[A-Za-z0-9_+/=-]{16,512}$")


def download(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
    with urllib.request.urlopen(request, timeout=45) as response:
        length = response.headers.get("Content-Length")
        if length and int(length) > MAX_BYTES:
            raise ValueError(f"declared response too large: {length}")
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("response too large")
    if not data.strip():
        raise ValueError("empty response")
    return data


def normalize_mtproto_url(raw: str) -> str | None:
    value = raw.strip().rstrip(",;)]}`")
    parsed = urllib.parse.urlparse(value)
    if parsed.scheme.lower() == "tg" and parsed.netloc.lower() == "proxy":
        query = urllib.parse.parse_qs(parsed.query, keep_blank_values=False)
    elif parsed.scheme.lower() in {"http", "https"} and parsed.netloc.lower() in {"t.me", "telegram.me"}:
        if parsed.path.rstrip("/").lower() != "/proxy":
            return None
        query = urllib.parse.parse_qs(parsed.query, keep_blank_values=False)
    else:
        return None
    host = (query.get("server") or [""])[0].strip().strip("[]").rstrip(".")
    port_text = (query.get("port") or [""])[0]
    secret = (query.get("secret") or [""])[0].strip()
    try:
        port = int(port_text)
    except ValueError:
        return None
    if not host or len(host) > 253 or port not in range(1, 65536) or not SECRET.fullmatch(secret):
        return None
    return "tg://proxy?" + urllib.parse.urlencode({"server": host, "port": port, "secret": secret})


def extract_mtproto(texts: list[str]) -> list[str]:
    unique: dict[str, str] = {}
    for text in texts:
        for match in PROXY_URL.finditer(text):
            url = normalize_mtproto_url(match.group(0))
            if not url:
                continue
            query = urllib.parse.parse_qs(urllib.parse.urlparse(url).query)
            key = f"{query['server'][0].lower()}:{query['port'][0]}:{query['secret'][0].lower()}"
            unique.setdefault(key, url)
            if len(unique) >= MAX_LINES:
                return list(unique.values())
    return list(unique.values())


def validate_json(name: str, data: bytes) -> None:
    parsed = json.loads(data.decode("utf-8"))
    if not isinstance(parsed, (list, dict)):
        raise ValueError(f"{name}: JSON root must be list or object")


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    downloaded: dict[str, bytes] = {}
    failures: list[str] = []
    for name, url in FEEDS.items():
        try:
            data = download(url)
            if name.endswith(".json"):
                validate_json(name, data)
            downloaded[name] = data
            print(f"downloaded {name}: {len(data)} bytes")
        except Exception as error:
            failures.append(f"{name}: {error}")

    critical = {"kort_all.txt", "kort_verified.json", "kort_stats.json", "shablin_valid.txt"}
    missing_critical = critical.difference(downloaded)
    if missing_critical:
        print("critical feeds failed: " + ", ".join(sorted(missing_critical)), file=sys.stderr)
        for failure in failures:
            print(failure, file=sys.stderr)
        return 1

    for existing in OUTPUT.iterdir():
        if existing.is_file() and existing.name not in downloaded and existing.name not in {"mtproto_merged.txt", "manifest.json"}:
            existing.unlink()
    for name, data in downloaded.items():
        (OUTPUT / name).write_bytes(data)

    mtproto_inputs = [
        data.decode("utf-8", errors="replace")
        for name, data in downloaded.items()
        if name not in {"kort_socks5.txt", "hookzof_socks5.txt", "kort_stats.json"}
    ]
    merged = extract_mtproto(mtproto_inputs)
    if len(merged) < 10:
        print(f"merged MTProto list is unexpectedly small: {len(merged)}", file=sys.stderr)
        return 1
    (OUTPUT / "mtproto_merged.txt").write_text("\n".join(merged) + "\n", encoding="utf-8")

    manifest = {
        "sources": {name: url for name, url in FEEDS.items()},
        "downloaded": sorted(downloaded),
        "failed": failures,
        "mtproto_merged_count": len(merged),
    }
    (OUTPUT / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"merged MTProto: {len(merged)}")
    if failures:
        print("non-critical failures: " + "; ".join(failures), file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
