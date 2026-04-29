package com.savantle.backend.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

data class MlbTeam(
    val id: Int,
    val name: String,
    val abbreviation: String,
    val league: String,
    val division: String
)

data class MlbPlayer(
    val mlbamId: Int,
    val fullName: String,
    val position: String,
    val team: MlbTeam
)

@Service
class MlbRosterService {

    private val log = LoggerFactory.getLogger(MlbRosterService::class.java)
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

    fun fetchActiveRosters(): List<MlbPlayer> {
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

    private fun fetchTeams(year: Int): List<MlbTeam> {
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

            MlbTeam(id = id, name = name, abbreviation = abbr, league = league, division = division)
        }
    }

    private fun fetchRoster(team: MlbTeam, rosterType: String): List<MlbPlayer> {
        val url = "https://statsapi.mlb.com/api/v1/teams/${team.id}/roster/$rosterType"
        val json = get(url)
        val root = mapper.readTree(json)

        return root.path("roster").mapNotNull { p ->
            val person = p.path("person")
            val pos = p.path("position")
            val id = person.path("id").asInt()
            val name = person.path("fullName").asText()
            val posAbbr = pos.path("abbreviation").asText()

            if (id <= 0 || name.isBlank() || posAbbr.isBlank()) return@mapNotNull null
            MlbPlayer(mlbamId = id, fullName = name, position = posAbbr, team = team)
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
