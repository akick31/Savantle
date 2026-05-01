package com.savantle.backend.services

import com.savantle.backend.model.DailyPlayer
import com.savantle.backend.repositories.DailyPlayerRepository
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.time.LocalDate
import kotlin.random.Random

@Service
class DailyPlayerService(
    private val mlbRosterService: MlbRosterService,
    private val screenshotService: ScreenshotService,
    private val repository: DailyPlayerRepository,
    private val entityManager: EntityManager,
    @Value("\${savantle.curator.days-ahead:7}") private val daysAhead: Int,
    @Value("\${savantle.qualification.batter-min-pa:75}") private val minBatterPa: Int,
    @Value("\${savantle.qualification.pitcher-min-ip:15.0}") private val minPitcherIp: Double,
    @Value("\${savantle.qualification.early-season-days:30}") private val earlySeasonDays: Long
) {

    private val log = LoggerFactory.getLogger(DailyPlayerService::class.java)

    @Volatile private var rosterCache: List<MlbPlayer> = emptyList()
    @Volatile private var rosterCacheDate: LocalDate? = null
    @Volatile private var allPlayerNames: List<String> = emptyList()
    @Volatile private var qualifiedIds: Set<Int> = emptySet()
    @Volatile private var seasonStartDate: LocalDate? = null

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
            val year = LocalDate.now().year
            val players = mlbRosterService.fetchActiveRosters()
            if (players.isNotEmpty()) {
                rosterCache = players
                rosterCacheDate = LocalDate.now()
                allPlayerNames = players.map { it.fullName }.distinct().sorted()
                log.info("Roster refreshed: ${players.size} active players")
            }
            if (seasonStartDate == null) {
                seasonStartDate = mlbRosterService.fetchSeasonStartDate(year)
                log.info("Season start date: $seasonStartDate")
            }
            val ids = mlbRosterService.fetchQualifiedPlayerIds(year, minBatterPa, minPitcherIp)
            qualifiedIds = ids
            log.info("Qualification pool: ${ids.size} players")
        } catch (e: Exception) {
            log.error("Failed to refresh roster", e)
        }
    }

    private fun isEarlySeason(date: LocalDate): Boolean {
        val start = seasonStartDate ?: return true
        return date < start.plusDays(earlySeasonDays)
    }

    @Transactional
    fun curateUpcomingDays() {
        val today = LocalDate.now()
        val futureDate = today.plusDays(daysAhead.toLong())
        val players = rosterCache
        if (players.isEmpty()) {
            log.warn("Roster cache empty; skipping curation")
            return
        }

        if (repository.existsByGameDate(futureDate)) {
            log.info("Player already curated for $futureDate")
            return
        }

        val curated = curateForDate(futureDate, players)
        if (curated != null) {
            repository.save(curated)
            log.info("Curated $futureDate: ${curated.fullName}")
        } else {
            log.warn("Could not curate a player for $futureDate")
        }
    }

    @Transactional
    fun curateSpecificPlayerForDate(date: LocalDate, playerName: String): Map<String, Any> {
        if (playerName.isBlank()) {
            throw IllegalArgumentException("Player name is required")
        }

        if (rosterCache.isEmpty() || rosterCacheDate != LocalDate.now()) {
            refreshRoster()
        }
        val players = rosterCache
        if (players.isEmpty()) {
            throw IllegalStateException("Roster cache is empty")
        }

        val normalized = normalizeForSearch(playerName)
        val candidate = players.firstOrNull { normalizeForSearch(it.fullName) == normalized }
            ?: throw IllegalArgumentException("Player not found in current MLB roster: $playerName")

        val pitcherPositions = setOf("SP", "RP", "P")
        val isPitcher = candidate.position in pitcherPositions
        val result = screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
            ?: throw IllegalStateException("Could not capture screenshot for ${candidate.fullName}")

        repository.deleteByGameDate(date)
        entityManager.flush()
        val saved = repository.save(
            DailyPlayer(
                gameDate = date,
                mlbamId = candidate.mlbamId,
                fullName = candidate.fullName,
                normalizedName = normalizeForSearch(candidate.fullName),
                position = candidate.position,
                throwingHand = candidate.throwingHand,
                isPitcher = isPitcher,
                teamName = candidate.team.name,
                teamAbbr = candidate.team.abbreviation,
                league = candidate.team.league,
                division = candidate.team.division,
                savantUrl = result.savantUrl,
                screenshot = result.pngBytes
            )
        )
        log.info("Manually curated $date: ${saved.fullName}")

        return mapOf(
            "date" to saved.gameDate.toString(),
            "fullName" to saved.fullName,
            "position" to formatPosition(saved),
            "mlbamId" to saved.mlbamId.toString(),
            "teamName" to saved.teamName
        )
    }

    private fun curateForDate(date: LocalDate, pool: List<MlbPlayer>): DailyPlayer? {
        val pitcherPositions = setOf("SP", "RP", "P")

        // Apply qualification filter unless we're in the early-season grace period
        val qualifiedPool = if (isEarlySeason(date) || qualifiedIds.isEmpty()) {
            log.info("Early season or no qualification data — using full pool for $date")
            pool
        } else {
            val filtered = pool.filter { it.mlbamId in qualifiedIds }
            log.info("Qualification filter: ${pool.size} -> ${filtered.size} players for $date")
            if (filtered.isEmpty()) pool else filtered
        }

        // Collect mlbamIds used in the past 30 days to avoid repeats
        val recentIds = repository
            .findByGameDateBetween(date.minusDays(30), date.minusDays(1))
            .map { it.mlbamId }
            .toSet()

        // Prefer candidates not seen recently; fall back to full qualified pool if needed
        val preferred = qualifiedPool.filter { it.mlbamId !in recentIds }
        val candidates = (if (preferred.isNotEmpty()) preferred else qualifiedPool).shuffled(Random)

        for (candidate in candidates) {
            val isPitcher = candidate.position in pitcherPositions
            val result = screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
                ?: continue

            return DailyPlayer(
                gameDate = date,
                mlbamId = candidate.mlbamId,
                fullName = candidate.fullName,
                normalizedName = normalizeForSearch(candidate.fullName),
                position = candidate.position,
                throwingHand = candidate.throwingHand,
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
        val pitcherPositions = setOf("SP", "RP", "P")
        val today = LocalDate.now()

        val upcomingPlayers = repository
            .findByGameDateBetween(today, today.plusDays(daysAhead.toLong()))

        val upcomingEntries = upcomingPlayers.associate {
            it.fullName to if (it.isPitcher) "PITCHER" else "BATTER"
        }

        val rosterEntries = rosterCache.associate {
            it.fullName to if (it.position in pitcherPositions) "PITCHER" else "BATTER"
        }

        return (rosterEntries + upcomingEntries)
            .entries
            .sortedBy { it.key }
            .map { (name, type) ->
                mapOf(
                    "fullName" to name,
                    "normalizedName" to normalizeForSearch(name),
                    "playerType" to type
                )
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
            1 -> mapOf("type" to "POSITION", "label" to "Position", "value" to formatPosition(player))
            2 -> mapOf("type" to "LEAGUE", "label" to "League", "value" to player.league)
            3 -> mapOf("type" to "DIVISION", "label" to "Division", "value" to player.division)
            4 -> mapOf("type" to "TEAM", "label" to "Team", "value" to player.teamName)
            else -> emptyMap()
        }
    }

    private fun buildPlayerInfo(player: DailyPlayer): Map<String, String> {
        return mapOf(
            "fullName" to player.fullName,
            "position" to formatPosition(player),
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

    private fun formatPosition(player: DailyPlayer): String {
        if (!player.isPitcher) return player.position
        return when (player.throwingHand?.uppercase()) {
            "L" -> "LHP"
            "R" -> "RHP"
            else -> "P"
        }
    }

    fun isReady(): Boolean = repository.existsByGameDate(LocalDate.now())
}
