# Savantle

A daily Wordle-style Baseball Savant percentiles guessing game. Each day a mystery MLB player is revealed through their Baseball Savant percentile rankings chart with their identity hidden. Players get up to 5 guesses, with hints revealed after each incorrect guess.

## Stack

| Layer | Tech |
|---|---|
| Frontend | React, TypeScript, Vite, Tailwind CSS |
| Backend | Spring Boot 3.3.5 (Kotlin), MariaDB |
| Screenshots | Playwright (headless Chromium) |

## Game Mechanics

- One mystery MLB player per day, selected from the active 26-man roster
- Players have 5 guesses to identify the mystery player
- Hints are revealed progressively after each wrong guess: Position, League, Division, Team
- Daily mode tracks win streaks and guess distribution
- Previous Day and Random Player modes are available without affecting daily stats

## REST API

Base path: `/api/v1/savantle`

| Method | Path | Description |
|---|---|---|
| GET | `/daily` | Returns date and player type for today (no player identity) |
| GET | `/screenshot/{date}` | PNG percentile chart, cached 24 hours |
| GET | `/players` | Full searchable player list |
| POST | `/guess` | Submit a guess; returns result, hints, and player info on game over |
| GET | `/available-dates` | Dates with available games for the replay picker |
| GET | `/random-date` | Returns a random available past date |
| POST | `/random-player/new` | Creates a new random game session |
| POST | `/random-player/guess` | Submit a guess against a random game |
| POST | `/contact` | Sends a contact form email |
| POST | `/admin/curate` | Manually set a player for a specific date (auth-gated) |
| GET | `/admin/analytics` | Analytics summary (auth-gated) |

## Dev Setup

**Backend**

```bash
cd backend
./gradlew bootRun
```

Requires Java 17+. Configuration is in `src/main/resources/application.properties` (not committed). The backend runs on port 163 by default.

**Frontend**

```bash
cd frontend
npm install
npm run dev
```

The dev server runs on port 3000 and proxies `/api` to `localhost:163`.

## Player Curation

A scheduled job runs daily at 4:00 AM and curates a player 7 days ahead from the active roster. Qualification minimums are 75 plate appearances for batters and 15 innings pitched for pitchers (waived in the first 30 days of the season). A second job at 4:10 AM refreshes the screenshot for today's player so percentile data stays current.

## Linting

The backend uses ktlint via the `org.jlleitschuh.gradle.ktlint` Gradle plugin.

```bash
cd backend
./gradlew ktlintCheck   # check for violations
./gradlew ktlintFormat  # auto-fix violations
```
