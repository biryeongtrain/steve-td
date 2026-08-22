#!/usr/bin/env python3
"""Fetch one SemionTD API route, optionally following metrics cursors."""

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BASE_URL = "https://semiontd.biryeong.kim"


def parse_params(items):
    params = {}
    for item in items:
        if "=" not in item:
            raise ValueError(f"expected key=value, got {item!r}")
        key, value = item.split("=", 1)
        if not key:
            raise ValueError("query key must not be empty")
        params[key] = value
    return params


def fetch(path, params):
    if not path.startswith("/api/v1/") and path != "/api/health":
        raise ValueError("endpoint must start with /api/v1/ or equal /api/health")
    query = f"?{urlencode(params)}" if params else ""
    request = Request(f"{BASE_URL}{path}{query}", headers={"User-Agent": "semiontd-live-balance-analysis/1"})
    with urlopen(request, timeout=20) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("endpoint", nargs="?", help="API path, for example /api/v1/stats")
    parser.add_argument("params", nargs="*", help="query parameters as key=value")
    parser.add_argument("--all-pages", action="store_true", help="follow every next_cursor")
    parser.add_argument("--max-pages", type=int, default=100, help="safety limit for --all-pages")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        assert parse_params(["builderId=semion-td:warlock_towers", "limit=2"]) == {
            "builderId": "semion-td:warlock_towers",
            "limit": "2",
        }
        print("ok")
        return
    if not args.endpoint:
        parser.error("endpoint is required")
    if args.max_pages < 1:
        parser.error("--max-pages must be at least 1")

    params = parse_params(args.params)
    first = fetch(args.endpoint, params)
    if not args.all_pages or "metrics" not in first:
        json.dump(first, sys.stdout, ensure_ascii=False, indent=2)
        print()
        return

    metrics = list(first["metrics"])
    cursor = first.get("next_cursor")
    pages = 1
    while cursor and pages < args.max_pages:
        page = fetch(args.endpoint, {**params, "cursor": cursor})
        metrics.extend(page["metrics"])
        cursor = page.get("next_cursor")
        pages += 1
    if cursor:
        raise RuntimeError(f"stopped after {pages} pages; increase --max-pages")
    json.dump({"metrics": metrics, "next_cursor": None, "pages": pages}, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    try:
        main()
    except (HTTPError, URLError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)
