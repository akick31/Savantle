#!/usr/bin/env python3
import sys

from curl_cffi import requests


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: fetch_url.py <url>", file=sys.stderr)
        return 2

    url = sys.argv[1]
    try:
        response = requests.get(url, impersonate="chrome124", timeout=15)
    except Exception as exc:
        print(f"REQUEST_ERROR: {exc}", file=sys.stderr)
        return 1

    if response.status_code != 200:
        print(f"HTTP_{response.status_code}", file=sys.stderr)
        return 1

    sys.stdout.write(response.text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
