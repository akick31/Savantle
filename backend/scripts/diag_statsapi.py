#!/usr/bin/env python3
"""Probe every statsapi.mlb.com request pattern the backend uses and report status codes.

Run this on the VPS to see which requests MLB's WAF rejects (409/403/406) there:
  python3 diag_statsapi.py

Includes variants of the bulk stats call so a single run shows which parameters
are safe. Per-team /roster/40Man and the hydrate param are NOT probed — both
were dropped from the backend (WAF rejected them too often to be worth the
latency, and hydrate is deterministically rejected).
"""

import sys
import time

from curl_cffi import requests

YEAR = time.localtime().tm_year

PROBES = [
    ("teams (used)", f"https://statsapi.mlb.com/api/v1/teams?sportId=1&season={YEAR}"),
    ("all players — sole roster source (used)", f"https://statsapi.mlb.com/api/v1/sports/1/players?season={YEAR}"),
    ("season dates (used)", f"https://statsapi.mlb.com/api/v1/seasons/{YEAR}?sportId=1"),
    (
        "bulk hitting stats (used)",
        f"https://statsapi.mlb.com/api/v1/stats?stats=season&group=hitting&gameType=R&season={YEAR}&limit=100&offset=0&playerPool=All",
    ),
    (
        "bulk hitting stats, offset page (used)",
        f"https://statsapi.mlb.com/api/v1/stats?stats=season&group=hitting&gameType=R&season={YEAR}&limit=100&offset=500&playerPool=All",
    ),
    (
        "bulk pitching stats (used)",
        f"https://statsapi.mlb.com/api/v1/stats?stats=season&group=pitching&gameType=R&season={YEAR}&limit=100&offset=0&playerPool=All",
    ),
    (
        "bulk stats without playerPool (variant)",
        f"https://statsapi.mlb.com/api/v1/stats?stats=season&group=hitting&gameType=R&season={YEAR}&limit=100&offset=0",
    ),
    (
        "bulk stats limit=50 (variant)",
        f"https://statsapi.mlb.com/api/v1/stats?stats=season&group=hitting&gameType=R&season={YEAR}&limit=50&offset=0&playerPool=All",
    ),
    (
        "single pitcher stats (used)",
        f"https://statsapi.mlb.com/api/v1/people/660271/stats?stats=season&group=pitching&season={YEAR}&gameType=R",
    ),
    (
        "savant batter CSV (fallback, used)",
        f"https://baseballsavant.mlb.com/leaderboard/custom?year={YEAR}&type=batter&filter=&min=1"
        "&selections=pa&chart=false&x=pa&y=pa&r=no&chartType=beeswarm&sort=1&sortDir=desc&csv=true",
    ),
    (
        "savant pitcher CSV (fallback, used)",
        f"https://baseballsavant.mlb.com/leaderboard/custom?year={YEAR}&type=pitcher&filter=&min=1"
        "&selections=p_formatted_ip,p_starting_p&chart=false&x=p_formatted_ip&y=p_formatted_ip"
        "&r=no&chartType=beeswarm&sort=1&sortDir=desc&csv=true",
    ),
]


def main() -> int:
    failures = 0
    for label, url in PROBES:
        try:
            response = requests.get(url, impersonate="chrome124", timeout=15)
            status = response.status_code
            size = len(response.content)
            ok = status == 200
        except Exception as exc:
            status, size, ok = f"ERROR ({exc})", 0, False
        if not ok and "known bad" not in label:
            failures += 1
        marker = "OK " if ok else "FAIL"
        print(f"[{marker}] {status:>4} {size:>9}B  {label}\n            {url}")
        time.sleep(1)
    print(f"\n{failures} unexpected failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
