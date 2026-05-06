# Savantle — Claude Context

## What it is
Savantle is a daily baseball guessing game (Wordle-style). Each day a mystery MLB player is shown via their Baseball Savant percentile rankings screenshot (identity hidden). Players get 5 guesses, with hints revealed after each wrong guess.

## Stack

| Layer | Tech |
|---|---|
| Frontend | React + TypeScript + Vite, Tailwind CSS (custom `sv-*` color tokens) |
| Backend | Spring Boot 3.3.5 (Kotlin), MariaDB, Playwright (screenshot capture) |
| Build | Gradle (Kotlin DSL) for backend, npm/Vite for frontend |

The frontend proxies `/api` to the backend in dev. In production the backend serves on port 163; frontend is a separate static build.

## Key files

### Backend
```
backend/src/main/kotlin/com/savantle/backend/
  controllers/GameController.kt        — all REST endpoints (/api/v1/savantle/*)
  services/
    DailyPlayerService.kt              — player curation, guess validation, hint logic, player list
    MLBRosterService.kt                — fetches active rosters + qualified player IDs from statsapi.mlb.com
    ScreenshotService.kt               — Playwright headless Chrome → Baseball Savant percentile screenshot
    GameResponseService.kt             — thin wrapper converting service results to ResponseEntity
    ContactService.kt                  — sends contact form emails via Spring Mail (Gmail SMTP)
  model/
    DailyPlayer.kt                     — JPA entity (game_date unique key, screenshot as LONGBLOB)
    MLBPlayer.kt / MLBTeam.kt          — in-memory roster models
    dto/                               — GuessRequest, ManualCurateRequest, ContactRequest
  repositories/DailyPlayerRepository.kt
  resources/application.properties    — port, DB creds, cron schedule, mail config
```

### Frontend
```
frontend/src/
  App.tsx                              — root layout, footer, modal state
  components/
    GamePlay.tsx                       — active game UI (hints panel, guess counter, search input)
    HintDisplay.tsx                    — wrong-guess rows with Baseball Savant Page links
    PercentileDisplay.tsx              — fetches + shows the screenshot
    PlayerSearch.tsx                   — autocomplete input (filters by playerType BATTER|PITCHER)
    EndScreen.tsx                      — win/loss reveal with player info + Savant link
    HowToPlay.tsx                      — rules modal
    Header.tsx                         — nav bar with HowToPlay / Stats / Settings buttons
    ContactModal.tsx                   — contact form (posts to /api/v1/savantle/contact)
    Modal.tsx                          — generic modal wrapper
    StatsModal.tsx / SettingsModal.tsx
  hooks/
    useGameState.ts                    — all game logic (load, guess, persist to localStorage)
    useStats.ts / useSettings.ts
  services/api.ts                      — fetch wrappers for all backend endpoints
  types/index.ts                       — shared TypeScript types
  utils/normalize.ts                   — normalizeForSearch (strips accents, lowercases)
```

## REST API  (`/api/v1/savantle`)

| Method | Path | Notes |
|---|---|---|
| GET | `/daily?date=YYYY-MM-DD` | Returns `{ date, playerType }` — never reveals the player |
| GET | `/screenshot/{date}` | Returns PNG bytes; cached 24h |
| GET | `/players` | Full searchable player list `{ fullName, normalizedName, playerType, mlbamId }[]` |
| POST | `/guess` | Body: `{ playerName, guessNumber, date }` → `{ correct, gameOver?, hints?, playerInfo? }` |
| POST | `/contact` | Body: `{ name, email, subject, message }` → sends email to apkicklighter@gmail.com |
| POST | `/admin/curate` | Auth-gated; manually sets a player for a date |

## Game mechanics

- **5 guesses max**, one mystery player per day
- **Hint schedule** (triggered on wrong guess number):
  - Guess 1 → Position (e.g., SS, 3B, LHP, RHP)
  - Guess 2 → League (AL / NL)
  - Guess 3 → Division (e.g., AL East)
  - Guess 4 → Team name
- **Early confirmation** (shown in green, before scheduled): only when guessed player shares the same **team** (confirms team + division + league) or **division** (confirms division + league). Same-league-only or same-position-only matches do NOT confirm anything early — this prevents users deducing the answer by absence.
- Hints always show the correct answer's value; `confirmed: true` means the guess matched that attribute.

## Player curation

- Runs daily at 4:00 AM via `@Scheduled` cron in `DailyPlayerService`
- Curates a player **7 days ahead** (`savantle.curator.days-ahead=7`) from the active 26-man roster
- Qualification filter: batters ≥ 75 PA, pitchers ≥ 15 IP (waived in first 30 days of season)
- A second job at 4:10 AM **refreshes today's screenshot** so percentile data is current on game day
- Screenshots captured via Playwright navigating to `baseballsavant.mlb.com/savant-player/{slug}-{mlbamId}?stats=statcast-r-{pitcher|batter}`

## Two-way players (e.g., Ohtani)
Players with MLB position `"TWP"` are returned as **two** `MLBPlayer` entries in the roster cache — one as `SP` (pitcher) and one as `DH` (batter). This ensures they appear in the autocomplete for whichever game type is active.

## Player list / autocomplete
`getPlayerList()` keys entries by `normalizedName|playerType`, so TWP players can have two entries. Today's and upcoming curated players from the DB always override the roster cache (ensures the daily player is guessable even if they leave the active roster mid-week).

## Data storage
- Single MariaDB table: `daily_player` — unique on `game_date`
- Screenshot stored as `LONGBLOB`; served via `/screenshot/{date}` with 24h cache header
- Game state on the client is localStorage-keyed by date (`savantle-game-YYYY-MM-DD`)

## Dev setup
```bash
# Backend
cd backend && ./gradlew bootRun

# Frontend
cd frontend && npm install && npm run dev
```

Frontend dev server proxies `/api` to `localhost:163`.

## Config notes (`application.properties`)
- `spring.mail.password` must be set to a **Gmail App Password** (not the account password) for the contact form to work. Generate at myaccount.google.com → Security → App passwords.
- `savantle.admin.api-key` gates the `/admin/curate` endpoint.
- DB credentials are in plaintext in `application.properties` — do not commit secrets to public repos.
