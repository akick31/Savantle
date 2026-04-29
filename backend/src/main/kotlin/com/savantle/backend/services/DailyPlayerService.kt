package com.savantle.backend.services

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.LocalDate

data class FullPlayerData(
    val mlbamId: Int,
    val fullName: String,
    val normalizedName: String,
    val position: String,
    val isPitcher: Boolean,
    val teamName: String,
    val teamAbbr: String,
    val league: String,
    val division: String,
    val percentiles: PlayerPercentiles
)

@Service
class DailyPlayerService(
    private val mlbRosterService: MlbRosterService,
    private val baseballSavantService: BaseballSavantService
) {

    private val log = LoggerFactory.getLogger(DailyPlayerService::class.java)

    @Volatile private var cachedPlayers: List<FullPlayerData> = emptyList()
    @Volatile private var cachedDailyPlayer: FullPlayerData? = null
    @Volatile private var cacheLoadedDate: LocalDate? = null

    init {
        refreshCache()
    }

    @Scheduled(cron = "0 5 0 * * *")
    fun refreshCache() {
        log.info("Refreshing player cache...")
        try {
            val rosterPlayers = mlbRosterService.fetchActiveRosters()
            log.info("Fetched ${rosterPlayers.size} active roster players")

            val percentileData = baseballSavantService.fetchPercentiles()
            log.info("Fetched percentile data for ${percentileData.size} players")

            val pitcherPositions = setOf("SP", "RP", "P")
            val fullPlayers = rosterPlayers.mapNotNull { player ->
                val perc = percentileData[player.mlbamId] ?: return@mapNotNull null
                FullPlayerData(
                    mlbamId = player.mlbamId,
                    fullName = player.fullName,
                    normalizedName = normalizeForSearch(player.fullName),
                    position = player.position,
                    isPitcher = player.position in pitcherPositions,
                    teamName = player.team.name,
                    teamAbbr = player.team.abbreviation,
                    league = player.team.league,
                    division = player.team.division,
                    percentiles = perc
                )
            }

            cachedPlayers = fullPlayers
            cacheLoadedDate = LocalDate.now()
            cachedDailyPlayer = selectPlayerForDate(fullPlayers, LocalDate.now())
            log.info("Cache ready: ${fullPlayers.size} players, today's player: ${cachedDailyPlayer?.fullName}")
        } catch (e: Exception) {
            log.error("Failed to refresh cache", e)
        }
    }

    fun getDailyPlayerResponse(date: LocalDate = LocalDate.now()): Map<String, Any> {
        val player = getPlayerForDate(date)
        return mapOf(
            "date" to date.toString(),
            "playerType" to if (player.isPitcher) "PITCHER" else "BATTER",
            "stats" to player.percentiles.stats
        )
    }

    fun getPlayerList(): List<Map<String, String>> {
        return cachedPlayers
            .distinctBy { it.fullName }
            .sortedBy { it.fullName }
            .map { mapOf("fullName" to it.fullName, "normalizedName" to it.normalizedName) }
    }

    fun validateGuess(playerName: String, date: LocalDate, guessNumber: Int): Map<String, Any> {
        val player = getPlayerForDate(date)
        val normalizedGuess = normalizeForSearch(playerName)
        val correct = normalizedGuess == player.normalizedName

        if (correct) {
            return mapOf(
                "correct" to true,
                "playerInfo" to buildPlayerInfo(player)
            )
        }

        val gameOver = guessNumber >= 5
        val result = mutableMapOf<String, Any>(
            "correct" to false,
            "gameOver" to gameOver
        )

        if (gameOver) {
            result["playerInfo"] = buildPlayerInfo(player)
        } else {
            result["hint"] = buildHint(player, guessNumber)
        }

        return result
    }

    private fun buildHint(player: FullPlayerData, guessNumber: Int): Map<String, String> {
        return when (guessNumber) {
            1 -> mapOf("type" to "POSITION", "label" to "Position", "value" to player.position)
            2 -> mapOf("type" to "LEAGUE", "label" to "League", "value" to player.league)
            3 -> mapOf("type" to "DIVISION", "label" to "Division", "value" to player.division)
            4 -> mapOf("type" to "TEAM", "label" to "Team", "value" to player.teamName)
            else -> emptyMap()
        }
    }

    private fun buildPlayerInfo(player: FullPlayerData): Map<String, String> {
        return mapOf(
            "fullName" to player.fullName,
            "position" to player.position,
            "teamName" to player.teamName,
            "teamAbbr" to player.teamAbbr,
            "league" to player.league,
            "division" to player.division,
            "mlbamId" to player.mlbamId.toString()
        )
    }

    private fun getPlayerForDate(date: LocalDate): FullPlayerData {
        if (cachedPlayers.isEmpty()) throw IllegalStateException("Player cache not loaded")
        if (date == LocalDate.now() && cachedDailyPlayer != null) return cachedDailyPlayer!!
        return selectPlayerForDate(cachedPlayers, date)
    }

    private fun selectPlayerForDate(players: List<FullPlayerData>, date: LocalDate): FullPlayerData {
        if (players.isEmpty()) throw IllegalStateException("No players available")
        val seed = date.year.toLong() * 10000 + date.monthValue * 100 + date.dayOfMonth
        val index = (seed % players.size).toInt()
        return players[index]
    }

    fun normalizeForSearch(name: String): String {
        val nfd = Normalizer.normalize(name, Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
            .lowercase()
            .trim()
    }

    fun isReady(): Boolean = cachedPlayers.isNotEmpty()
}
