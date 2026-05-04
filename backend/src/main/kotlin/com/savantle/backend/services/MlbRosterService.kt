package com.savantle.backend.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.savantle.backend.model.MLBPlayer
import com.savantle.backend.model.MLBTeam
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

@Service
class MLBRosterService {

    private val log = LoggerFactory.getLogger(MLBRosterService::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val mapper = ObjectMapper()

    private val divisionMap = mapOf(
        200 to "AL West",
        201 to "AL East",
        202 to "AL Central",
        203 to "NL West",
        204 to "NL East",
        205 to "NL Central"
    )

    fun fetchSeasonStartDate(year: Int): LocalDate? {
        return try {
            val json = get("https://statsapi.mlb.com/api/v1/seasons/$year?sportId=1")
            val dateStr = mapper.readTree(json)
                .path("seasons").path(0)
                .path("regularSeasonStartDate").asText()
            if (dateStr.isBlank()) null else LocalDate.parse(dateStr)
        } catch (e: Exception) {
            log.warn("Could not fetch season start date for $year: ${e.message}")
            null
        }
    }

    fun fetchQualifiedPlayerIds(year: Int, minBatterPa: Int, minPitcherIp: Double): Set<Int> {
        val qualified = mutableSetOf<Int>()

        try {
            val battingJson = get(
                "https://statsapi.mlb.com/api/v1/stats?stats=season&group=hitting" +
                "&gameType=R&season=$year&limit=5000&playerPool=All"
            )
            mapper.readTree(battingJson).path("stats").forEach { group ->
                group.path("splits").forEach { split ->
                    val id = split.path("player").path("id").asInt()
                    val pa = split.path("stat").path("plateAppearances").asInt()
                    if (id > 0 && pa >= minBatterPa) qualified.add(id)
                }
            }
            log.info("Qualified batters (PA >= $minBatterPa): ${qualified.size}")
        } catch (e: Exception) {
            log.warn("Failed to fetch batting qualification stats: ${e.message}")
        }

        val beforePitching = qualified.size
        try {
            val pitchingJson = get(
                "https://statsapi.mlb.com/api/v1/stats?stats=season&group=pitching" +
                "&gameType=R&season=$year&limit=5000&playerPool=All"
            )
            mapper.readTree(pitchingJson).path("stats").forEach { group ->
                group.path("splits").forEach { split ->
                    val id = split.path("player").path("id").asInt()
                    val ipStr = split.path("stat").path("inningsPitched").asText("0")
                    val ip = parseInningsPitched(ipStr)
                    if (id > 0 && ip >= minPitcherIp) qualified.add(id)
                }
            }
            log.info("Qualified pitchers (IP >= $minPitcherIp): ${qualified.size - beforePitching}")
        } catch (e: Exception) {
            log.warn("Failed to fetch pitching qualification stats: ${e.message}")
        }

        return qualified
    }

    private fun parseInningsPitched(ip: String): Double {
        return try {
            val parts = ip.split(".")
            val innings = parts[0].toDoubleOrNull() ?: 0.0
            val outs = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            innings + (outs / 3.0)
        } catch (_: Exception) { 0.0 }
    }

    fun fetchActiveRosters(): List<MLBPlayer> {
        val year = LocalDate.now().year
        val teams = fetchTeams(year)
        log.info("Found ${teams.size} MLB teams for $year")

        return teams.flatMap { team ->
            try {
                val players = fetchRoster(team, "active")
                if (players.isEmpty()) fetchRoster(team, "40Man") else players
            } catch (e: Exception) {
                log.warn("Failed to fetch roster for ${team.name}: ${e.message}")
                emptyList()
            }
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
            val league = when (leagueId) {
                103 -> "AL"
                104 -> "NL"
                else -> return@mapNotNull null
            }
            val division = divisionMap[divisionId] ?: return@mapNotNull null

            MLBTeam(id = id, name = name, abbreviation = abbr, league = league, division = division)
        }
    }

    private fun fetchRoster(team: MLBTeam, rosterType: String): List<MLBPlayer> {
        val url = "https://statsapi.mlb.com/api/v1/teams/${team.id}/roster/$rosterType?hydrate=person"
        val json = get(url)
        val root = mapper.readTree(json)

        return root.path("roster").flatMap { p ->
            val person = p.path("person")
            val pos = p.path("position")
            val id = person.path("id").asInt()
            val name = person.path("fullName").asText()
            val posAbbr = pos.path("abbreviation").asText()
            val handCode = person.path("pitchHand").path("code").asText().uppercase().trim()
                .takeIf { it == "L" || it == "R" || it == "S" }

            if (id <= 0 || name.isBlank() || posAbbr.isBlank()) return@flatMap emptyList()

            // Two-way players appear as both pitcher and batter
            if (posAbbr == "TWP") {
                listOf(
                    MLBPlayer(mlbamId = id, fullName = name, position = "SP", throwingHand = handCode, team = team),
                    MLBPlayer(mlbamId = id, fullName = name, position = "DH", throwingHand = handCode, team = team)
                )
            } else {
                listOf(MLBPlayer(mlbamId = id, fullName = name, position = posAbbr, throwingHand = handCode, team = team))
            }
        }
    }

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}
