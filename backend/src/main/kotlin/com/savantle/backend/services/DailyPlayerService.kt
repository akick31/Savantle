package com.savantle.backend.services

import com.savantle.backend.model.DailyPlayer
import com.savantle.backend.repositories.DailyPlayerRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.time.LocalDate

@Service
class DailyPlayerService(
    private val mlbRosterService: MlbRosterService,
    private val screenshotService: ScreenshotService,
    private val repository: DailyPlayerRepository,
    @Value("\${savantle.curator.days-ahead:7}") private val daysAhead: Int
) {

    private val log = LoggerFactory.getLogger(DailyPlayerService::class.java)

    @Volatile private var rosterCache: List<MlbPlayer> = emptyList()
    @Volatile private var rosterCacheDate: LocalDate? = null
    @Volatile private var allPlayerNames: List<String> = emptyList()

    @PostConstruct
    fun onStartup() {
        Thread {
            try {
                refreshRoster()
                curateUpcomingDays()
            } catch (e: Exception) {
                log.error("Startup curation failed", e)
            }
        }.start()
    }

    @Scheduled(cron = "\${savantle.curator.cron:0 0 4 * * *}")
    fun scheduledCurate() {
        refreshRoster()
        curateUpcomingDays()
    }

    private fun refreshRoster() {
        try {
            val players = mlbRosterService.fetchActiveRosters()
            if (players.isNotEmpty()) {
                rosterCache = players
                rosterCacheDate = LocalDate.now()
                allPlayerNames = players.map { it.fullName }.distinct().sorted()
                log.info("Roster refreshed: ${players.size} active players")
            }
        } catch (e: Exception) {
            log.error("Failed to refresh roster", e)
        }
    }

    @Transactional
    fun curateUpcomingDays() {
        val today = LocalDate.now()
        val players = rosterCache
        if (players.isEmpty()) {
            log.warn("Roster cache empty; skipping curation")
            return
        }

        for (offset in 0..daysAhead) {
            val date = today.plusDays(offset.toLong())
            if (repository.existsByGameDate(date)) continue
            val curated = curateForDate(date, players)
            if (curated != null) {
                repository.save(curated)
                log.info("Curated $date: ${curated.fullName}")
            } else {
                log.warn("Could not curate a player for $date")
            }
        }
    }

    private fun curateForDate(date: LocalDate, pool: List<MlbPlayer>): DailyPlayer? {
        val pitcherPositions = setOf("SP", "RP", "P")
        val seed = date.year.toLong() * 10000 + date.monthValue * 100 + date.dayOfMonth
        val ordered = pool.sortedBy { it.mlbamId }
        val size = ordered.size
        val startIdx = (seed % size).toInt()

        for (i in 0 until size) {
            val candidate = ordered[(startIdx + i) % size]
            val isPitcher = candidate.position in pitcherPositions
            val result = screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
                ?: continue

            return DailyPlayer(
                gameDate = date,
                mlbamId = candidate.mlbamId,
                fullName = candidate.fullName,
                normalizedName = normalizeForSearch(candidate.fullName),
                position = candidate.position,
                isPitcher = isPitcher,
                teamName = candidate.team.name,
                teamAbbr = candidate.team.abbreviation,
                league = candidate.team.league,
                division = candidate.team.division,
                savantUrl = result.savantUrl,
                screenshot = result.pngBytes
            )
        }
        return null
    }

    fun getDailyPlayerResponse(date: LocalDate): Map<String, Any> {
        val player = repository.findByGameDate(date)
            ?: throw IllegalStateException("No player available for $date")
        return mapOf(
            "date" to date.toString(),
            "playerType" to if (player.isPitcher) "PITCHER" else "BATTER"
        )
    }

    fun getScreenshot(date: LocalDate): ByteArray? {
        return repository.findByGameDate(date)?.screenshot
    }

    fun getPlayerList(): List<Map<String, String>> {
        return allPlayerNames.map {
            mapOf("fullName" to it, "normalizedName" to normalizeForSearch(it))
        }
    }

    fun validateGuess(playerName: String, date: LocalDate, guessNumber: Int): Map<String, Any> {
        val player = repository.findByGameDate(date)
            ?: throw IllegalStateException("No player available for $date")
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

    private fun buildHint(player: DailyPlayer, guessNumber: Int): Map<String, String> {
        return when (guessNumber) {
            1 -> mapOf("type" to "POSITION", "label" to "Position", "value" to player.position)
            2 -> mapOf("type" to "LEAGUE", "label" to "League", "value" to player.league)
            3 -> mapOf("type" to "DIVISION", "label" to "Division", "value" to player.division)
            4 -> mapOf("type" to "TEAM", "label" to "Team", "value" to player.teamName)
            else -> emptyMap()
        }
    }

    private fun buildPlayerInfo(player: DailyPlayer): Map<String, String> {
        return mapOf(
            "fullName" to player.fullName,
            "position" to player.position,
            "teamName" to player.teamName,
            "teamAbbr" to player.teamAbbr,
            "league" to player.league,
            "division" to player.division,
            "mlbamId" to player.mlbamId.toString(),
            "savantUrl" to player.savantUrl
        )
    }

    fun normalizeForSearch(name: String): String {
        val nfd = Normalizer.normalize(name, Normalizer.Form.NFD)
        return nfd.replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase()
            .trim()
    }

    fun isReady(): Boolean = repository.existsByGameDate(LocalDate.now())
}
