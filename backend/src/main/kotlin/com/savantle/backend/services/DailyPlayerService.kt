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
    @Value("\${savantle.qualification.starter-ip-target:120.0}") private val starterIpTarget: Double,
    @Value("\${savantle.qualification.reliever-ip-target:50.0}") private val relieverIpTarget: Double,
    @Value("\${savantle.qualification.early-season-days:30}") private val earlySeasonDays: Long,
) {
    companion object {
        private const val LIVE_SCREENSHOT_TTL_HOURS = 24L
        private const val SEASON_LENGTH_DAYS = 183.0
        private const val MIN_EXPECTED_ROSTER_SIZE = 500
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

    @Scheduled(cron = "\${savantle.validator.cron:0 30 23 * * *}")
    @Transactional
    fun validateAndSwapTomorrowsPlayer() {
        val tomorrow = LocalDate.now().plusDays(1)
        val player = dailyPlayerRepository.findByGameDate(tomorrow) ?: return

        refreshRoster()

        val onRoster = rosterCache.any { it.mlbamId == player.mlbamId && it.onActiveRoster }
        val meetsQualification = isEarlySeason(tomorrow) || qualifiedIds.isEmpty() || player.mlbamId in qualifiedIds

        if (onRoster && meetsQualification) {
            log.info("Tomorrow's player ${player.fullName} is still eligible")
            return
        }

        val reason = if (!onRoster) "no longer on active roster" else "no longer meets qualification requirements"
        log.warn("${player.fullName} is $reason for $tomorrow — finding replacement")

        val replacement =
            curateForDate(tomorrow, rosterCache.filter { it.mlbamId != player.mlbamId })
                ?: run {
                    log.warn("No replacement found for $tomorrow — keeping ${player.fullName}")
                    return
                }

        dailyPlayerRepository.deleteByGameDate(tomorrow)
        entityManager.flush()
        dailyPlayerRepository.save(replacement)
        log.info("Swapped ${player.fullName} → ${replacement.fullName} for $tomorrow")
    }

    @Scheduled(cron = "0 30 9 * * *", zone = "UTC")
    @Transactional
    fun refreshNextDayScreenshot() {
        val tomorrow = LocalDate.now().plusDays(1)
        val player = dailyPlayerRepository.findByGameDate(tomorrow) ?: return
        refreshRoster()
        val result = screenshotService.capturePercentiles(player.mlbamId, player.fullName, player.isPitcher)
        if (result != null) {
            player.screenshot = result.pngBytes
            player.savantUrl = result.savantUrl
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
            if (player.isPitcher) {
                val stats = mlbRosterService.fetchPitcherStats(player.mlbamId, tomorrow.year)
                if (stats != null) {
                    player.inningsPitched = stats.first
                    player.gamesStarted = stats.second
                }
            }
            dailyPlayerRepository.save(player)
            log.info("Refreshed screenshot for tomorrow: ${player.fullName}")
        } else {
            log.warn("Could not refresh screenshot for tomorrow: ${player.fullName}")
        }
    }

    private fun refreshRoster() {
        try {
            val year = LocalDate.now().year
            var players = mlbRosterService.fetchActiveRosters()
            if (players.size < MIN_EXPECTED_ROSTER_SIZE) {
                log.warn("Roster fetch returned only ${players.size} players (expected >= $MIN_EXPECTED_ROSTER_SIZE) — retrying once")
                players = mlbRosterService.fetchActiveRosters()
            }
            if (players.size >= MIN_EXPECTED_ROSTER_SIZE) {
                rosterCache = players
                rosterCacheDate = LocalDate.now()
                log.info("Roster refreshed: ${players.size} active players")
            } else if (players.isNotEmpty()) {
                log.warn("Roster fetch still only ${players.size} players after retry — keeping existing cache of ${rosterCache.size}")
            }
            if (seasonStartDate == null) {
                seasonStartDate = mlbRosterService.fetchSeasonStartDate(year)
                log.info("Season start date: $seasonStartDate")
            }
            val (starterFloor, relieverFloor) = computePitcherFloors()
            val ids = mlbRosterService.fetchQualifiedPlayerIds(year, minBatterPa, starterFloor, relieverFloor)
            qualifiedIds = ids
            log.info("Qualification pool: ${ids.size} players")
        } catch (e: Exception) {
            log.error("Failed to refresh roster", e)
        }
    }

    private fun computePitcherFloors(): Pair<Double, Double> {
        val start = seasonStartDate ?: return Pair(minPitcherIp, minPitcherIp)
        val progress = ((LocalDate.now().toEpochDay() - start.toEpochDay()) / SEASON_LENGTH_DAYS).coerceIn(0.0, 1.0)
        return Pair(
            maxOf(minPitcherIp, progress * starterIpTarget),
            maxOf(minPitcherIp, progress * relieverIpTarget),
        )
    }

    fun isEarlySeason(date: LocalDate): Boolean {
        val start = seasonStartDate ?: return true
        return date < start.plusDays(earlySeasonDays)
    }

    private fun isPlayerEligible(player: MLBPlayer): Boolean =
        player.onActiveRoster &&
            (isEarlySeason(LocalDate.now()) || qualifiedIds.isEmpty() || player.mlbamId in qualifiedIds)

    private fun eligibilityReason(
        player: MLBPlayer,
        isPitcher: Boolean,
    ): String {
        if (!player.onActiveRoster) {
            return if (player.rosterStatus?.startsWith("Injured", ignoreCase = true) == true) {
                "Warning: ${player.fullName} is currently on the IL"
            } else {
                "Warning: ${player.fullName} is not on an active MLB roster"
            }
        }
        val threshold = if (isPitcher) "IP threshold for pitchers" else "PA threshold for batters"
        return "Warning: ${player.fullName} hasn't reached the $threshold"
    }

    fun getRosterCache(): List<MLBPlayer> = rosterCache

    fun buildRandomPool(excludedMlbamIds: Set<Int>): List<MLBPlayer> {
        val activeRoster = rosterCache.filter { it.onActiveRoster }
        val basePool =
            if (!isEarlySeason(LocalDate.now()) && qualifiedIds.isNotEmpty()) {
                val filtered = activeRoster.filter { it.mlbamId in qualifiedIds }
                filtered.ifEmpty { activeRoster }
            } else {
                activeRoster
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

        val pitcherStats = if (isPitcher) mlbRosterService.fetchPitcherStats(candidate.mlbamId, date.year) else null
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
                    inningsPitched = pitcherStats?.first,
                    gamesStarted = pitcherStats?.second,
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
        val activePool = pool.filter { it.onActiveRoster }
        val qualifiedPool =
            if (isEarlySeason(date) || qualifiedIds.isEmpty()) {
                log.info("Early season or no qualification data — using full pool for $date")
                activePool
            } else {
                val filtered = activePool.filter { it.mlbamId in qualifiedIds }
                log.info("Qualification filter: ${activePool.size} -> ${filtered.size} players for $date")
                if (filtered.isEmpty()) activePool else filtered
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

            val pitcherStats = if (isPitcher) mlbRosterService.fetchPitcherStats(candidate.mlbamId, date.year) else null
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
                inningsPitched = pitcherStats?.first,
                gamesStarted = pitcherStats?.second,
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

    fun getPlayerList(): List<Map<String, Any>> {
        val today = LocalDate.now()
        val combined = LinkedHashMap<String, Map<String, Any>>()
        val todaysPlayerMlbamId = dailyPlayerRepository.findByGameDate(today)?.mlbamId

        for (player in rosterCache) {
            val normalized = PlayerUtils.normalizeForSearch(player.fullName)
            val types =
                when {
                    player.position == "TWP" -> listOf("PITCHER", "BATTER")
                    player.position in PITCHER_POSITIONS -> listOf("PITCHER")
                    else -> listOf("BATTER")
                }
            val eligible = player.mlbamId == todaysPlayerMlbamId || isPlayerEligible(player)
            for (type in types) {
                val entry =
                    mutableMapOf<String, Any>(
                        "fullName" to player.fullName,
                        "normalizedName" to normalized,
                        "playerType" to type,
                        "mlbamId" to player.mlbamId.toString(),
                        "eligible" to eligible,
                    )
                if (!eligible) {
                    entry["eligibilityReason"] = eligibilityReason(player, type == "PITCHER")
                }
                combined["$normalized|$type"] = entry
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
            combined.putIfAbsent(
                "$normalized|$type",
                mapOf(
                    "fullName" to player.fullName,
                    "normalizedName" to normalized,
                    "playerType" to type,
                    "mlbamId" to player.mlbamId.toString(),
                    "eligible" to true,
                ),
            )
        }

        return combined.values.sortedBy { it["fullName"] as String }
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
