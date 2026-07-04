#!/usr/bin/env bash
# Force-recurate upcoming daily_player entries via the existing /admin/curate endpoint,
# guaranteeing no repeats across the recreated set.
#
# /admin/curate deletes any existing entry for the given date and re-runs curateForDate,
# which already excludes any player used in the last 60 days. Calling it sequentially,
# oldest date first, means each new pick is committed before the next date is curated, so
# the run can't repeat a player against itself. Today is intentionally left alone — running
# this only touches tomorrow onward so no in-progress or completed game changes underneath
# a player who already loaded today's puzzle.
#
# Usage:
#   SAVANTLE_ADMIN_KEY=<key> ./recurate_upcoming.sh [start_date] [end_date]
#   Defaults to tomorrow through 7 days out. Dates are YYYY-MM-DD.
#
# Run this on the VPS (or anywhere that can reach the public API) — it just calls the
# existing HTTPS endpoint, no local DB access needed.

set -euo pipefail

API_URL="${SAVANTLE_API_URL:-https://savantle.com/api/v1}"
: "${SAVANTLE_ADMIN_KEY:?SAVANTLE_ADMIN_KEY must be set}"

START_DATE="${1:-$(date -d '+1 day' +%F)}"
END_DATE="${2:-$(date -d '+7 day' +%F)}"

echo "Recurating $START_DATE through $END_DATE (sequential, no repeats)..."

current="$START_DATE"
while [[ ! "$current" > "$END_DATE" ]]; do
  echo "-- Curating $current"
  response=$(curl -s -w '\n%{http_code}' -X POST "$API_URL/admin/curate" \
    -H "X-Admin-Key: $SAVANTLE_ADMIN_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"date\":\"$current\"}")
  status="${response##*$'\n'}"
  body="${response%$'\n'*}"
  echo "   HTTP $status: $body"
  if [[ "$status" != "200" ]]; then
    echo "Curation failed for $current — stopping so later dates don't curate against a gap." >&2
    exit 1
  fi
  current=$(date -d "$current + 1 day" +%F)
done

echo "Done."
