package com.savantle.backend.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.savantle.backend.model.MLBPlayer
import com.savantle.backend.model.MLBTeam
import com.savantle.backend.model.PitcherLine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.LocalDate

@Service
class MLBRosterService {
    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val WAF_RETRY_DELAY_MS = 10000L
        private const val ROSTER_FETCH_PACING_MS = 1000L
        private const val ROSTER_COOLDOWN_MS = 60000L
        private const val STATS_PAGE_SIZE = 100
        private const val STATS_PAGE_PACING_MS = 1000L
        private const val FETCH_SCRIPT_PATH = "scripts/fetch_url.py"
        private val WAF_STATUSES = setOf(403, 406, 409)
    }

    private val log = LoggerFactory.getLogger(MLBRosterService::class.java)
    private val mapper = ObjectMapper()

    private val divisionMap =
        mapOf(
            200 to "AL West",
            201 to "AL East",
            202 to "AL Central",
            203 to "NL West",
            204 to "NL East",
            205 to "NL Central",
        )

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

    fun fetchQualificationStats(year: Int): Pair<Map<Int, Int>, Map<Int, PitcherLine>> {
        val batterPa = mutableMapOf<Int, Int>()
        fetchAllStatSplits(year, "hitting").forEach { split ->
            val id = split.path("player").path("id").asInt()
            val pa = split.path("stat").path("plateAppearances").asInt()
            if (id > 0) batterPa[id] = pa
        }

        val pitcherLines = mutableMapOf<Int, PitcherLine>()
        fetchAllStatSplits(year, "pitching").forEach { split ->
            val id = split.path("player").path("id").asInt()
            val stat = split.path("stat")
            val ip = parseInningsPitched(stat.path("inningsPitched").asText("0"))
            val gs = stat.path("gamesStarted").asInt(0)
            if (id > 0) pitcherLines[id] = PitcherLine(ip, gs)
        }

        log.info("Fetched qualification stats: ${batterPa.size} batters, ${pitcherLines.size} pitchers")
        return batterPa to pitcherLines
    }

    fun fetchQualificationStatsWithFallback(year: Int): Pair<Map<Int, Int>, Map<Int, PitcherLine>> {
        return try {
            fetchQualificationStats(year).also { log.info("Qualification stats source: statsapi bulk") }
        } catch (e: Exception) {
            log.warn("Bulk statsapi qualification stats failed (${e.message}) — falling back to Baseball Savant")
            fetchQualificationStatsFromSavant(year).also { log.info("Qualification stats source: Baseball Savant CSV") }
        }
    }

    fun fetchQualificationStatsFromSavant(year: Int): Pair<Map<Int, Int>, Map<Int, PitcherLine>> {
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

    private fun fetchAllStatSplits(
        year: Int,
        group: String,
    ): List<JsonNode> {
        val splits = mutableListOf<JsonNode>()
        var offset = 0
        while (true) {
            val json =
                get(
                    "https://statsapi.mlb.com/api/v1/stats?stats=season&group=$group" +
                        "&gameType=R&season=$year&limit=$STATS_PAGE_SIZE&offset=$offset&playerPool=All",
                )
            val page = mutableListOf<JsonNode>()
            mapper.readTree(json).path("stats").forEach { g -> g.path("splits").forEach { page.add(it) } }
            splits.addAll(page)
            if (page.size < STATS_PAGE_SIZE) break
            offset += STATS_PAGE_SIZE
            Thread.sleep(STATS_PAGE_PACING_MS)
        }
        return splits
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

    fun fetchActiveRosters(): List<MLBPlayer> {
        val year = LocalDate.now().year
        val teams = fetchTeams(year)
        log.info("Found ${teams.size} MLB teams for $year")
        val pitchHands = fetchPitchHands(year)

        val players = mutableListOf<MLBPlayer>()
        var remaining = teams
        var pass = 1
        while (remaining.isNotEmpty() && pass <= 2) {
            if (pass > 1) {
                log.warn("${remaining.size} team rosters failed — retrying after ${ROSTER_COOLDOWN_MS / 1000}s cooldown")
                Thread.sleep(ROSTER_COOLDOWN_MS)
            }
            val failed = mutableListOf<MLBTeam>()
            for (team in remaining) {
                try {
                    players.addAll(fetchRoster(team, pitchHands))
                } catch (e: Exception) {
                    log.warn("Failed to fetch roster for ${team.name}: ${e.message}")
                    failed.add(team)
                }
                Thread.sleep(ROSTER_FETCH_PACING_MS)
            }
            remaining = failed
            pass++
        }
        return players
    }

    private fun fetchPitchHands(year: Int): Map<Int, String> {
        return try {
            val json = get("https://statsapi.mlb.com/api/v1/sports/1/players?season=$year")
            val hands = mutableMapOf<Int, String>()
            mapper.readTree(json).path("people").forEach { p ->
                val id = p.path("id").asInt()
                val code =
                    p.path("pitchHand").path("code").asText().uppercase().trim()
                        .takeIf { it == "L" || it == "R" || it == "S" }
                if (id > 0 && code != null) hands[id] = code
            }
            hands
        } catch (e: Exception) {
            log.warn("Failed to fetch pitch hands: ${e.message}")
            emptyMap()
        }
    }

    private fun fetchTeams(year: Int): List<MLBTeam> {
        val url = "https://statsapi.mlb.com/api/v1/teams?sportId=1&season=$year"
        val json = get(url)
        val root = mapper.readTree(json)

        return root.path("teams").mapNotNull { t ->
            val id = t.path("id").asInt()
            val name = t.path("teamName").asText()
            val abbr = t.path("abbreviation").asText()
            val leagueId = t.path("league").path("id").asInt()
            val divisionId = t.path("division").path("id").asInt()

            if (id <= 0 || name.isBlank() || abbr.isBlank()) return@mapNotNull null
            val league =
                when (leagueId) {
                    103 -> "AL"
                    104 -> "NL"
                    else -> return@mapNotNull null
                }
            val division = divisionMap[divisionId] ?: return@mapNotNull null

            MLBTeam(id = id, name = name, abbreviation = abbr, league = league, division = division)
        }
    }

    private fun fetchRoster(
        team: MLBTeam,
        pitchHands: Map<Int, String>,
    ): List<MLBPlayer> {
        val url = "https://statsapi.mlb.com/api/v1/teams/${team.id}/roster/40Man"
        val json = get(url)
        val root = mapper.readTree(json)

        return root.path("roster").flatMap { p ->
            val person = p.path("person")
            val pos = p.path("position")
            val id = person.path("id").asInt()
            val name = person.path("fullName").asText()
            val posAbbr = pos.path("abbreviation").asText()
            val handCode = pitchHands[id]
            val status = p.path("status").path("description").asText().takeIf { it.isNotBlank() }
            val onActiveRoster = p.path("status").path("code").asText() == "A"

            if (id <= 0 || name.isBlank() || posAbbr.isBlank()) return@flatMap emptyList()

            if (posAbbr == "TWP") {
                listOf(
                    MLBPlayer(
                        mlbamId = id,
                        fullName = name,
                        position = "SP",
                        throwingHand = handCode,
                        team = team,
                        onActiveRoster = onActiveRoster,
                        rosterStatus = status,
                    ),
                    MLBPlayer(
                        mlbamId = id,
                        fullName = name,
                        position = "DH",
                        throwingHand = handCode,
                        team = team,
                        onActiveRoster = onActiveRoster,
                        rosterStatus = status,
                    ),
                )
            } else {
                listOf(
                    MLBPlayer(
                        mlbamId = id,
                        fullName = name,
                        position = posAbbr,
                        throwingHand = handCode,
                        team = team,
                        onActiveRoster = onActiveRoster,
                        rosterStatus = status,
                    ),
                )
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
        val process = ProcessBuilder("python3", FETCH_SCRIPT_PATH, url).start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return Triple(exitCode, stdout, stderr)
    }
}
