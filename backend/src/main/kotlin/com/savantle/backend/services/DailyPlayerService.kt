package com.savantle.backend.services

import com.savantle.backend.model.DailyPlayer
import com.savantle.backend.model.MLBPlayer
import com.savantle.backend.repositories.DailyPlayerRepository
import com.savantle.backend.util.PlayerUtils
import com.savantle.backend.util.PlayerUtils.PITCHER_POSITIONS
import com.savantle.backend.util.toSnapshot
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
class DailyPlayerService(
    private val mlbRosterService: MLBRosterService,
    private val screenshotService: ScreenshotService,
    private val dailyPlayerRepository: DailyPlayerRepository,
    private val entityManager: EntityManager,
    @Value("\${savantle.curator.days-ahead:7}") private val daysAhead: Int,
    @Value("\${savantle.qualification.batter-min-pa:75}") private val minBatterPa: Int,
    @Value("\${savantle.qualification.pitcher-min-ip:15.0}") private val minPitcherIp: Double,
    @Value("\${savantle.qualification.early-season-days:30}") private val earlySeasonDays: Long,
) {
    companion object {
        private const val LIVE_SCREENSHOT_TTL_HOURS = 24L
    }

    private val log = LoggerFactory.getLogger(DailyPlayerService::class.java)

    @Volatile private var rosterCache: List<MLBPlayer> = emptyList()

    @Volatile private var rosterCacheDate: LocalDate? = null

    @Volatile private var qualifiedIds: Set<Int> = emptySet()

    @Volatile private var seasonStartDate: LocalDate? = null

    private data class CachedScreenshot(val bytes: ByteArray, val capturedAt: Instant)

    private val liveScreenshotCache = ConcurrentHashMap<String, CachedScreenshot>()

    @EventListener(ApplicationReadyEvent::class)
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

    @Scheduled(cron = "0 10 4 * * *")
    @Transactional
    fun refreshTodayScreenshot() {
        val today = LocalDate.now()
        val player = dailyPlayerRepository.findByGameDate(today) ?: return
        val result = screenshotService.capturePercentiles(player.mlbamId, player.fullName, player.isPitcher)
        if (result != null) {
            player.screenshot = result.pngBytes
            player.savantUrl = result.savantUrl
            // Sync team info in case the player was traded since curation
            val rosterPlayer = rosterCache.firstOrNull { it.mlbamId == player.mlbamId }
            if (rosterPlayer != null) {
                if (rosterPlayer.team.name != player.teamName) {
                    log.info("Team change detected for ${player.fullName}: ${player.teamName} -> ${rosterPlayer.team.name}")
                }
                player.teamName = rosterPlayer.team.name
                player.teamAbbr = rosterPlayer.team.abbreviation
                player.league = rosterPlayer.team.league
                player.division = rosterPlayer.team.division
            }
            dailyPlayerRepository.save(player)
            log.info("Refreshed screenshot for today: ${player.fullName}")
        } else {
            log.warn("Could not refresh screenshot for today: ${player.fullName}")
        }
    }

    private fun refreshRoster() {
        try {
            val year = LocalDate.now().year
            val players = mlbRosterService.fetchActiveRosters()
            if (players.isNotEmpty()) {
                rosterCache = players
                rosterCacheDate = LocalDate.now()
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

    fun isEarlySeason(date: LocalDate): Boolean {
        val start = seasonStartDate ?: return true
        return date < start.plusDays(earlySeasonDays)
    }

    fun getRosterCache(): List<MLBPlayer> = rosterCache

    fun buildRandomPool(excludedMlbamIds: Set<Int>): List<MLBPlayer> {
        val basePool =
            if (!isEarlySeason(LocalDate.now()) && qualifiedIds.isNotEmpty()) {
                val filtered = rosterCache.filter { it.mlbamId in qualifiedIds }
                filtered.ifEmpty { rosterCache }
            } else {
                rosterCache
            }
        return basePool.filter { it.mlbamId !in excludedMlbamIds }
    }

    @Transactional
    fun curateUpcomingDays() {
        val futureDate = LocalDate.now().plusDays(daysAhead.toLong())
        val players = rosterCache
        if (players.isEmpty()) {
            log.warn("Roster cache empty; skipping curation")
            return
        }
        if (dailyPlayerRepository.existsByGameDate(futureDate)) {
            log.info("Player already curated for $futureDate")
            return
        }
        val curated = curateForDate(futureDate, players)
        if (curated != null) {
            dailyPlayerRepository.save(curated)
            log.info("Curated $futureDate: ${curated.fullName}")
        } else {
            log.warn("Could not curate a player for $futureDate")
        }
    }

    @Transactional
    fun curateAutoForDate(date: LocalDate): Map<String, Any> {
        if (rosterCache.isEmpty() || rosterCacheDate != LocalDate.now()) refreshRoster()
        val players = rosterCache
        if (players.isEmpty()) throw IllegalStateException("Roster cache is empty")

        if (dailyPlayerRepository.existsByGameDate(date)) {
            dailyPlayerRepository.deleteByGameDate(date)
            entityManager.flush()
        }

        val curated =
            curateForDate(date, players)
                ?: throw IllegalStateException("Could not find an eligible player or capture a screenshot for $date")

        val saved = dailyPlayerRepository.save(curated)
        log.info("Auto-curated $date: ${saved.fullName}")
        return mapOf(
            "date" to saved.gameDate.toString(),
            "fullName" to saved.fullName,
            "position" to PlayerUtils.formatPosition(saved.isPitcher, saved.throwingHand, saved.position),
            "mlbamId" to saved.mlbamId.toString(),
            "teamName" to saved.teamName,
        )
    }

    @Transactional
    fun curateSpecificPlayerForDate(
        date: LocalDate,
        playerName: String,
    ): Map<String, Any> {
        require(playerName.isNotBlank()) { "Player name is required" }
        require(playerName.length <= 100) { "Player name too long" }

        if (rosterCache.isEmpty() || rosterCacheDate != LocalDate.now()) {
            refreshRoster()
        }
        val players = rosterCache
        if (players.isEmpty()) throw IllegalStateException("Roster cache is empty")

        val normalized = PlayerUtils.normalizeForSearch(playerName)
        val candidate =
            players.firstOrNull { PlayerUtils.normalizeForSearch(it.fullName) == normalized }
                ?: throw IllegalArgumentException("Player not found in current MLB roster: $playerName")

        val isPitcher = candidate.position in PITCHER_POSITIONS
        val result =
            screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
                ?: throw IllegalStateException("Could not capture screenshot for ${candidate.fullName}")

        dailyPlayerRepository.deleteByGameDate(date)
        entityManager.flush()
        val saved =
            dailyPlayerRepository.save(
                DailyPlayer(
                    gameDate = date,
                    mlbamId = candidate.mlbamId,
                    fullName = candidate.fullName,
                    normalizedName = PlayerUtils.normalizeForSearch(candidate.fullName),
                    position = candidate.position,
                    throwingHand = candidate.throwingHand,
                    isPitcher = isPitcher,
                    teamName = candidate.team.name,
                    teamAbbr = candidate.team.abbreviation,
                    league = candidate.team.league,
                    division = candidate.team.division,
                    savantUrl = result.savantUrl,
                    screenshot = result.pngBytes,
                ),
            )
        log.info("Manually curated $date: ${saved.fullName}")

        return mapOf(
            "date" to saved.gameDate.toString(),
            "fullName" to saved.fullName,
            "position" to PlayerUtils.formatPosition(saved.isPitcher, saved.throwingHand, saved.position),
            "mlbamId" to saved.mlbamId.toString(),
            "teamName" to saved.teamName,
        )
    }

    private fun curateForDate(
        date: LocalDate,
        pool: List<MLBPlayer>,
    ): DailyPlayer? {
        val qualifiedPool =
            if (isEarlySeason(date) || qualifiedIds.isEmpty()) {
                log.info("Early season or no qualification data — using full pool for $date")
                pool
            } else {
                val filtered = pool.filter { it.mlbamId in qualifiedIds }
                log.info("Qualification filter: ${pool.size} -> ${filtered.size} players for $date")
                if (filtered.isEmpty()) pool else filtered
            }

        val recentIds =
            dailyPlayerRepository
                .findByGameDateBetween(date.minusDays(60), date.minusDays(1))
                .map { it.mlbamId }
                .toSet()

        val preferred = qualifiedPool.filter { it.mlbamId !in recentIds }
        val candidates = (if (preferred.isNotEmpty()) preferred else qualifiedPool).shuffled(Random)

        for (candidate in candidates) {
            val isPitcher = candidate.position in PITCHER_POSITIONS
            val result =
                screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
                    ?: continue

            return DailyPlayer(
                gameDate = date,
                mlbamId = candidate.mlbamId,
                fullName = candidate.fullName,
                normalizedName = PlayerUtils.normalizeForSearch(candidate.fullName),
                position = candidate.position,
                throwingHand = candidate.throwingHand,
                isPitcher = isPitcher,
                teamName = candidate.team.name,
                teamAbbr = candidate.team.abbreviation,
                league = candidate.team.league,
                division = candidate.team.division,
                savantUrl = result.savantUrl,
                screenshot = result.pngBytes,
            )
        }
        return null
    }

    fun getDailyPlayerResponse(date: LocalDate): Map<String, Any> {
        val player =
            dailyPlayerRepository.findByGameDate(date)
                ?: throw IllegalStateException("No player available for $date")
        return mapOf(
            "date" to date.toString(),
            "playerType" to if (player.isPitcher) "PITCHER" else "BATTER",
        )
    }

    fun getScreenshot(date: LocalDate): ByteArray? = dailyPlayerRepository.findByGameDate(date)?.screenshot

    fun getPlayerList(): List<Map<String, String>> {
        val today = LocalDate.now()
        val combined = LinkedHashMap<String, Map<String, String>>()

        for (player in rosterCache) {
            val normalized = PlayerUtils.normalizeForSearch(player.fullName)
            val types =
                when {
                    player.position == "TWP" -> listOf("PITCHER", "BATTER")
                    player.position in PITCHER_POSITIONS -> listOf("PITCHER")
                    else -> listOf("BATTER")
                }
            for (type in types) {
                combined["$normalized|$type"] =
                    mapOf(
                        "fullName" to player.fullName,
                        "normalizedName" to normalized,
                        "playerType" to type,
                        "mlbamId" to player.mlbamId.toString(),
                    )
            }
        }

        val allCurated =
            dailyPlayerRepository.findByGameDateBetween(
                LocalDate.of(2000, 1, 1),
                today.plusDays(daysAhead.toLong()),
            )
        for (player in allCurated) {
            val normalized = PlayerUtils.normalizeForSearch(player.fullName)
            val type = if (player.isPitcher) "PITCHER" else "BATTER"
            combined["$normalized|$type"] =
                mapOf(
                    "fullName" to player.fullName,
                    "normalizedName" to normalized,
                    "playerType" to type,
                    "mlbamId" to player.mlbamId.toString(),
                )
        }

        return combined.values.sortedBy { it["fullName"] }
    }

    fun validateGuess(
        playerName: String,
        date: LocalDate,
        guessNumber: Int,
    ): Map<String, Any> {
        val player =
            dailyPlayerRepository.findByGameDate(date)
                ?: throw IllegalStateException("No player available for $date")
        val correct = PlayerUtils.normalizeForSearch(playerName) == player.normalizedName

        if (correct) {
            return mapOf("correct" to true, "playerInfo" to PlayerUtils.buildPlayerInfo(player.toSnapshot()))
        }

        val gameOver = guessNumber >= 5
        val result = mutableMapOf<String, Any>("correct" to false, "gameOver" to gameOver)
        if (gameOver) {
            result["playerInfo"] = PlayerUtils.buildPlayerInfo(player.toSnapshot())
        } else {
            result["hints"] = PlayerUtils.buildHints(player.toSnapshot(), guessNumber, playerName, rosterCache)
        }
        return result
    }

    fun getLiveScreenshot(date: LocalDate): ByteArray? {
        val dateKey = date.toString()
        val cached = liveScreenshotCache[dateKey]
        if (cached != null && Instant.now().isBefore(cached.capturedAt.plusSeconds(LIVE_SCREENSHOT_TTL_HOURS * 3600))) {
            log.info("Returning cached live screenshot for $dateKey")
            return cached.bytes
        }
        val player = dailyPlayerRepository.findByGameDate(date) ?: return null
        val result =
            screenshotService.capturePercentiles(player.mlbamId, player.fullName, player.isPitcher)
                ?: return null
        liveScreenshotCache[dateKey] = CachedScreenshot(result.pngBytes, Instant.now())
        return result.pngBytes
    }

    fun getAvailableDates(): List<String> {
        val today = LocalDate.now()
        return dailyPlayerRepository.findByGameDateBetween(LocalDate.of(2020, 1, 1), today.minusDays(1))
            .map { it.gameDate.toString() }
            .sorted()
    }

    fun getRandomPastDate(): String {
        val dates = getAvailableDates()
        require(dates.isNotEmpty()) { "No past games available" }
        return dates.random()
    }

    fun isReady(): Boolean = dailyPlayerRepository.existsByGameDate(LocalDate.now())
}
