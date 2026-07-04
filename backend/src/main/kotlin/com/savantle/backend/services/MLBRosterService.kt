package com.savantle.backend.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.savantle.backend.model.roster.MLBPlayer
import com.savantle.backend.model.roster.MLBTeam
import com.savantle.backend.model.roster.MlbTeams
import com.savantle.backend.model.roster.PitcherLine
import com.savantle.backend.util.PlayerUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.time.LocalDate

@Service
class MLBRosterService {
    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val WAF_RETRY_DELAY_MS = 10000L
        private const val FETCH_SCRIPT_PATH = "scripts/fetch_url.py"
        private val WAF_STATUSES = setOf(403, 406, 409)
        private val GAMEFEED_DATE_REGEX = Regex("""gamefeed\?gamePk=\d+&game_date=(\d{4}-\d{2}-\d{2})""")
    }

    private val log = LoggerFactory.getLogger(MLBRosterService::class.java)
    private val mapper = ObjectMapper()

    fun fetchSeasonStartDate(year: Int): LocalDate? {
        return try {
            val json = get("https://statsapi.mlb.com/api/v1/seasons/$year?sportId=1")
            val dateStr =
                mapper.readTree(json)
                    .path("seasons").path(0)
                    .path("regularSeasonStartDate").asText()
            if (dateStr.isBlank()) null else LocalDate.parse(dateStr)
        } catch (e: Exception) {
            log.warn("Could not fetch season start date for $year: ${e.message}")
            null
        }
    }

    /**
     * Baseball Savant only — statsapi's bulk /stats endpoint was tried as a fallback here and
     * dropped: it 406'd every attempt across multiple diagnostic runs, so it was dead weight.
     * Savant is a different host and has proven reliable throughout.
     */
    fun fetchQualificationStats(year: Int): Pair<Map<Int, Int>, Map<Int, PitcherLine>> {
        val batterPa = mutableMapOf<Int, Int>()
        for (row in parseSavantCsv(get(savantLeaderboardUrl(year, "batter", "pa")))) {
            val id = row["player_id"]?.toIntOrNull() ?: continue
            batterPa[id] = row["pa"]?.toIntOrNull() ?: 0
        }

        val pitcherLines = mutableMapOf<Int, PitcherLine>()
        for (row in parseSavantCsv(get(savantLeaderboardUrl(year, "pitcher", "p_formatted_ip,p_starting_p")))) {
            val id = row["player_id"]?.toIntOrNull() ?: continue
            val ip = parseInningsPitched(row["p_formatted_ip"] ?: "0")
            val gs = row["p_starting_p"]?.toIntOrNull() ?: 0
            pitcherLines[id] = PitcherLine(ip, gs)
        }

        if (batterPa.isEmpty() || pitcherLines.isEmpty()) {
            throw IOException("Savant qualification stats incomplete: ${batterPa.size} batters, ${pitcherLines.size} pitchers")
        }
        log.info("Fetched Savant qualification stats: ${batterPa.size} batters, ${pitcherLines.size} pitchers")
        return batterPa to pitcherLines
    }

    private fun savantLeaderboardUrl(
        year: Int,
        type: String,
        selections: String,
    ): String {
        val sortCol = selections.substringBefore(",")
        return "https://baseballsavant.mlb.com/leaderboard/custom?year=$year&type=$type&filter=&min=1" +
            "&selections=$selections&chart=false&x=$sortCol&y=$sortCol&r=no&chartType=beeswarm&sort=1&sortDir=desc&csv=true"
    }

    internal fun parseSavantCsv(csv: String): List<Map<String, String>> {
        val lines = csv.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()
        val header = parseCsvLine(lines.first())
        return lines.drop(1).map { line -> header.zip(parseCsvLine(line)).toMap() }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private fun parseInningsPitched(ip: String): Double {
        return try {
            val parts = ip.split(".")
            val innings = parts[0].toDoubleOrNull() ?: 0.0
            val outs = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            innings + (outs / 3.0)
        } catch (_: Exception) {
            0.0
        }
    }

    fun fetchPitcherStats(
        mlbamId: Int,
        year: Int,
    ): Pair<Double, Int>? {
        return try {
            val json =
                get(
                    "https://statsapi.mlb.com/api/v1/people/$mlbamId/stats?stats=season&group=pitching&season=$year&gameType=R",
                )
            val splits = mapper.readTree(json).path("stats").firstOrNull()?.path("splits") ?: return null
            val stat = splits.firstOrNull()?.path("stat") ?: return null
            val ipStr = stat.path("inningsPitched").asText("0")
            val ip = parseInningsPitched(ipStr)
            val gs = stat.path("gamesStarted").asInt(0)
            ip to gs
        } catch (e: Exception) {
            log.warn("Failed to fetch pitcher stats for mlbamId=$mlbamId year=$year: ${e.message}")
            null
        }
    }

    /**
     * Sourced from the player's own Baseball Savant page rather than statsapi's gameLog stat
     * type: Savant has proven far more reliable under the WAF, and the page already embeds
     * gamefeed links for every recent plate appearance/pitch. Any slug works — Savant 301s to
     * the canonical one — but we build the real one to skip that extra redirect. Best-effort,
     * single call per player, used only for the curated/random daily reveal (not bulk), so a
     * failure just means the game omits this detail.
     *
     * The embedded gamefeed links are NOT reliably in chronological order (other page widgets —
     * e.g. career-vs-this-team blurbs — interleave older dates after the real most recent game),
     * so the max date across all of them is taken rather than the first/last one found.
     */
    fun fetchLastGamePlayed(
        mlbamId: Int,
        fullName: String,
    ): LocalDate? {
        return try {
            val slug = PlayerUtils.toSlug(fullName)
            val html = get("https://baseballsavant.mlb.com/savant-player/$slug-$mlbamId")
            extractLastGamePlayed(html)
        } catch (e: Exception) {
            log.warn("Failed to fetch last game played for mlbamId=$mlbamId: ${e.message}")
            null
        }
    }

    internal fun extractLastGamePlayed(html: String): LocalDate? {
        val maxDate =
            GAMEFEED_DATE_REGEX.findAll(html)
                .map { it.groupValues[1] }
                .maxOrNull() ?: return null
        return LocalDate.parse(maxDate)
    }

    /**
     * Roster source is the single bulk /sports/1/players call — one request covering all 30
     * teams, far more reliable than 30 sequential per-team /roster/40Man calls (which were tried
     * as a status-enrichment pass and dropped: the WAF rejects them too often to be worth the
     * time, and the endpoint's own "active" field doesn't distinguish IL/optioned players anyway
     * — it's true for every player the endpoint returns). Every player is therefore
     * onActiveRoster=true with no rosterStatus; qualification stats remain the only eligibility
     * filter (see RosterDataService).
     */
    fun fetchActiveRosters(): List<MLBPlayer> {
        val year = LocalDate.now().year
        val teams = MlbTeams.ALL

        val players = fetchAllPlayers(year, teams.associateBy { it.id })
        log.info("Bulk player fetch: ${players.size} players across ${teams.size} teams")
        return players
    }

    private fun fetchAllPlayers(
        year: Int,
        teamsById: Map<Int, MLBTeam>,
    ): List<MLBPlayer> {
        val json = get("https://statsapi.mlb.com/api/v1/sports/1/players?season=$year")

        return mapper.readTree(json).path("people").flatMap { p ->
            val id = p.path("id").asInt()
            val name = p.path("fullName").asText()
            val posAbbr = p.path("primaryPosition").path("abbreviation").asText()
            val team = teamsById[p.path("currentTeam").path("id").asInt()]
            val handCode =
                p.path("pitchHand").path("code").asText().uppercase().trim()
                    .takeIf { it == "L" || it == "R" || it == "S" }

            if (id <= 0 || name.isBlank() || posAbbr.isBlank() || team == null) return@flatMap emptyList()

            if (posAbbr == "TWP") {
                listOf(
                    MLBPlayer(mlbamId = id, fullName = name, position = "SP", throwingHand = handCode, team = team),
                    MLBPlayer(mlbamId = id, fullName = name, position = "DH", throwingHand = handCode, team = team),
                )
            } else {
                listOf(MLBPlayer(mlbamId = id, fullName = name, position = posAbbr, throwingHand = handCode, team = team))
            }
        }
    }

    private fun get(url: String): String {
        var lastError = ""
        for (attempt in 1..MAX_RETRIES) {
            val (exitCode, stdout, stderr) = runFetchScript(url)
            if (exitCode == 0) return stdout
            lastError = stderr.trim()

            val status = Regex("HTTP_(\\d+)").find(lastError)?.groupValues?.get(1)?.toIntOrNull()
            val isWafRejection = status in WAF_STATUSES
            val retryable = status == null || status == 429 || isWafRejection || status >= 500
            if (!retryable) throw IOException("Fetch failed for $url: $lastError")
            if (attempt < MAX_RETRIES) {
                // MLB's WAF rejections (403/406/409) need a cooldown, not a quick retry
                val delay = if (isWafRejection) WAF_RETRY_DELAY_MS else RETRY_DELAY_MS
                Thread.sleep(delay * attempt)
            }
        }
        throw IOException("Fetch failed for $url after $MAX_RETRIES attempts: $lastError")
    }

    private fun runFetchScript(url: String): Triple<Int, String, String> {
        val process = ProcessBuilder("python3", scriptPath, url).start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return Triple(exitCode, stdout, stderr)
    }

    /**
     * "scripts/fetch_url.py" only resolves if the JVM's working directory is `backend/` — true
     * in Docker (WORKDIR=/app, matching where the jar and scripts/ are copied) and true for
     * `cd backend && ./gradlew bootRun`, but not if something (an IDE run config, a shell script)
     * launches the JVM from the repo root instead. Checked once and cached; falls back to the
     * plain relative path if neither candidate exists so the original error message still shows
     * the path that was actually tried.
     */
    private val scriptPath: String by lazy {
        val cwdRelative = File(FETCH_SCRIPT_PATH)
        val repoRootRelative = File("backend/$FETCH_SCRIPT_PATH")
        when {
            cwdRelative.isFile -> cwdRelative.path
            repoRootRelative.isFile -> repoRootRelative.path
            else -> FETCH_SCRIPT_PATH
        }
    }
}
