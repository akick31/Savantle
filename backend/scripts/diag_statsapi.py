#!/usr/bin/env python3
"""Probe every statsapi.mlb.com request pattern the backend uses and report status codes.

Run this on the VPS to see which requests MLB's WAF rejects (409/403/406) there:
  python3 diag_statsapi.py

statsapi's remaining footprint is small and deliberate: the bulk roster call (nothing
else can provide zero-stat/just-called-up players), season start date (rarely fetched,
cached once it succeeds), and the per-pitcher stats fallback (rare — only hit when a
pitcher is missing from Savant's stats entirely). Everything else that used to hit
statsapi (teams, bulk qualification stats, per-team roster status, hydrate) has been
either hardcoded (see MlbTeams.kt) or dropped for being unreliable / redundant with
Baseball Savant.
"""

import sys
import time

from curl_cffi import requests

YEAR = time.localtime().tm_year

PROBES = [
    ("all players — sole roster source (used)", f"https://statsapi.mlb.com/api/v1/sports/1/players?season={YEAR}"),
    ("season dates (used)", f"https://statsapi.mlb.com/api/v1/seasons/{YEAR}?sportId=1"),
    (
        "single pitcher stats (fallback, used)",
        f"https://statsapi.mlb.com/api/v1/people/660271/stats?stats=season&group=pitching&season={YEAR}&gameType=R",
    ),
    (
        "savant player page — last game played (fallback, used)",
        "https://baseballsavant.mlb.com/savant-player/shohei-ohtani-660271",
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
