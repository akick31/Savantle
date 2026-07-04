package com.savantle.backend.services

import com.savantle.backend.model.entity.DailyPlayer
import com.savantle.backend.model.roster.MLBPlayer
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
    private val rosterDataService: RosterDataService,
    private val screenshotService: ScreenshotService,
    private val alertService: AlertService,
    private val dailyPlayerRepository: DailyPlayerRepository,
    private val entityManager: EntityManager,
    @Value("\${savantle.curator.days-ahead:7}") private val daysAhead: Int,
) {
    companion object {
        private const val LIVE_SCREENSHOT_TTL_HOURS = 24L
        private const val REPEAT_WINDOW_DAYS = 60L
        private const val RELAXED_REPEAT_WINDOW_DAYS = 14L
        private const val MAX_SCREENSHOT_ATTEMPTS = 15
    }

    private val log = LoggerFactory.getLogger(DailyPlayerService::class.java)

    private data class CachedScreenshot(val bytes: ByteArray, val capturedAt: Instant)

    private val liveScreenshotCache = ConcurrentHashMap<String, CachedScreenshot>()

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        Thread {
            try {
                rosterDataService.refreshUntilReady()
                curateUpcomingDays()
            } catch (e: Exception) {
                log.error("Startup curation failed", e)
            }
        }.start()
    }

    @Scheduled(cron = "\${savantle.curator.cron:0 0 4 * * *}")
    fun scheduledCurate() {
        rosterDataService.refresh()
        curateUpcomingDays()
        reportCurationHealth()
    }

    private fun reportCurationHealth() {
        val today = LocalDate.now()
        val missing = (0..daysAhead).map { today.plusDays(it.toLong()) }.filter { !dailyPlayerRepository.existsByGameDate(it) }
        val rosterStale = !rosterDataService.isRosterUsable()
        val statsStale = !rosterDataService.areStatsUsable()
        if (missing.isEmpty() && !rosterStale && !statsStale) return

        val body =
            buildString {
                if (rosterStale) {
                    appendLine("Roster data is missing or stale — last successful fetch: ${rosterDataService.rosterTimestamp() ?: "never"}.")
                }
                if (statsStale) {
                    appendLine("Qualification stats are missing or stale — last successful fetch: ${rosterDataService.statsTimestamp() ?: "never"}.")
                }
                if (rosterStale || statsStale) {
                    appendLine("Run backend/scripts/diag_statsapi.py on the VPS to see which requests are failing.")
                    appendLine()
                }
                if (missing.isNotEmpty()) {
                    appendLine("No daily player is curated for: ${missing.joinToString(", ")}")
                }
            }
        log.error("Curation health check failed:\n$body")
        alertService.send("Curation needs attention", body)
    }

    @Scheduled(cron = "\${savantle.validator.cron:0 30 23 * * *}")
    @Transactional
    fun validateAndSwapTomorrowsPlayer() {
        val tomorrow = LocalDate.now().plusDays(1)
        val player = dailyPlayerRepository.findByGameDate(tomorrow) ?: return

        rosterDataService.refresh()
        if (!rosterDataService.isRosterUsable()) {
            log.warn("Roster data missing or stale — skipping eligibility validation for $tomorrow")
            return
        }

        val roster = rosterDataService.players()
        val onRoster = roster.any { it.mlbamId == player.mlbamId && it.onActiveRoster }
        val meetsQualification =
            rosterDataService.isEarlySeason(tomorrow) ||
                !rosterDataService.areStatsUsable() ||
                player.mlbamId in rosterDataService.qualifiedIds()

        if (onRoster && meetsQualification) {
            log.info("Tomorrow's player ${player.fullName} is still eligible")
            return
        }

        val reason = if (!onRoster) "no longer on active roster" else "no longer meets qualification requirements"
        log.warn("${player.fullName} is $reason for $tomorrow — finding replacement")

        val replacement =
            curateForDate(tomorrow, roster.filter { it.mlbamId != player.mlbamId })
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
        rosterDataService.refresh()
        val result = screenshotService.capturePercentiles(player.mlbamId, player.fullName, player.isPitcher)
        if (result != null) {
            player.screenshot = result.pngBytes
            player.savantUrl = result.savantUrl
            val rosterPlayer = rosterDataService.players().firstOrNull { it.mlbamId == player.mlbamId }
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
                val stats = rosterDataService.pitcherLineFor(player.mlbamId, tomorrow.year)
                if (stats != null) {
                    player.inningsPitched = stats.first
                    player.gamesStarted = stats.second
                }
            }
            val lastGamePlayed = rosterDataService.lastGamePlayedFor(player.mlbamId, player.fullName)
            if (lastGamePlayed != null) player.lastGamePlayed = lastGamePlayed
            dailyPlayerRepository.save(player)
            log.info("Refreshed screenshot for tomorrow: ${player.fullName}")
        } else {
            log.warn("Could not refresh screenshot for tomorrow: ${player.fullName}")
        }
    }

    @Transactional
    fun curateUpcomingDays() {
        val players = rosterDataService.players()
        if (players.isEmpty()) {
            log.warn("Roster cache empty; skipping curation")
            return
        }
        val today = LocalDate.now()
        for (offset in 0..daysAhead) {
            val date = today.plusDays(offset.toLong())
            if (dailyPlayerRepository.existsByGameDate(date)) continue
            val curated = curateForDate(date, players)
            if (curated != null) {
                dailyPlayerRepository.save(curated)
                log.info("Curated $date: ${curated.fullName}")
            } else {
                log.warn("Could not curate a player for $date")
            }
        }
    }

    @Transactional
    fun curateAutoForDate(date: LocalDate): Map<String, Any> {
        rosterDataService.ensureFreshForToday()
        val players = rosterDataService.players()
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
        return curationSummary(saved)
    }

    @Transactional
    fun curateSpecificPlayerForDate(
        date: LocalDate,
        playerName: String,
    ): Map<String, Any> {
        require(playerName.isNotBlank()) { "Player name is required" }
        require(playerName.length <= 100) { "Player name too long" }

        rosterDataService.ensureFreshForToday()
        val players = rosterDataService.players()
        if (players.isEmpty()) throw IllegalStateException("Roster cache is empty")

        val normalized = PlayerUtils.normalizeForSearch(playerName)
        val candidate =
            players.firstOrNull { PlayerUtils.normalizeForSearch(it.fullName) == normalized }
                ?: throw IllegalArgumentException("Player not found in current MLB roster: $playerName")

        val curated =
            buildDailyPlayer(date, candidate)
                ?: throw IllegalStateException("Could not capture screenshot for ${candidate.fullName}")

        dailyPlayerRepository.deleteByGameDate(date)
        entityManager.flush()
        val saved = dailyPlayerRepository.save(curated)
        log.info("Manually curated $date: ${saved.fullName}")
        return curationSummary(saved)
    }

    private fun curationSummary(saved: DailyPlayer): Map<String, Any> =
        mapOf(
            "date" to saved.gameDate.toString(),
            "fullName" to saved.fullName,
            "position" to PlayerUtils.formatPosition(saved.isPitcher, saved.throwingHand, saved.position),
            "mlbamId" to saved.mlbamId.toString(),
            "teamName" to saved.teamName,
        )

    private fun curateForDate(
        date: LocalDate,
        pool: List<MLBPlayer>,
    ): DailyPlayer? {
        if (!rosterDataService.isRosterUsable()) {
            log.error("Roster data missing or stale (last fetch: ${rosterDataService.rosterTimestamp()}) — refusing to curate for $date")
            return null
        }

        val activePool = pool.filter { it.onActiveRoster }
        val qualifiedPool =
            if (rosterDataService.isEarlySeason(date)) {
                log.info("Early season — using full active pool for $date")
                activePool
            } else {
                val qualifiedIds = rosterDataService.qualifiedIds()
                if (!rosterDataService.areStatsUsable() || qualifiedIds.isEmpty()) {
                    log.error("Qualification data missing or stale (last fetch: ${rosterDataService.statsTimestamp()}) — refusing to curate for $date")
                    return null
                }
                val filtered = activePool.filter { it.mlbamId in qualifiedIds }
                log.info("Qualification filter: ${activePool.size} -> ${filtered.size} players for $date")
                if (filtered.isEmpty()) {
                    log.error("No active players meet qualification thresholds — refusing to curate for $date")
                    return null
                }
                filtered
            }

        val recentPlayers = dailyPlayerRepository.findByGameDateBetween(date.minusDays(REPEAT_WINDOW_DAYS), date.minusDays(1))
        val recentIds = recentPlayers.map { it.mlbamId }.toSet()

        val preferred = qualifiedPool.filter { it.mlbamId !in recentIds }
        val candidatePool =
            if (preferred.isNotEmpty()) {
                preferred
            } else {
                val relaxedIds =
                    recentPlayers
                        .filter { it.gameDate >= date.minusDays(RELAXED_REPEAT_WINDOW_DAYS) }
                        .map { it.mlbamId }
                        .toSet()
                val relaxed = qualifiedPool.filter { it.mlbamId !in relaxedIds }
                if (relaxed.isEmpty()) {
                    log.error("All eligible players were used within the last $RELAXED_REPEAT_WINDOW_DAYS days — refusing to curate for $date")
                    return null
                }
                log.warn(
                    "All eligible players were used within $REPEAT_WINDOW_DAYS days — " +
                        "relaxing repeat window to $RELAXED_REPEAT_WINDOW_DAYS days for $date",
                )
                relaxed
            }
        val candidates = candidatePool.shuffled(Random)

        for (candidate in candidates.take(MAX_SCREENSHOT_ATTEMPTS)) {
            val curated = buildDailyPlayer(date, candidate)
            if (curated != null) return curated
        }
        return null
    }

    private fun buildDailyPlayer(
        date: LocalDate,
        candidate: MLBPlayer,
    ): DailyPlayer? {
        val isPitcher = candidate.position in PITCHER_POSITIONS
        val result = screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher) ?: return null
        val pitcherStats = if (isPitcher) rosterDataService.pitcherLineFor(candidate.mlbamId, date.year) else null
        val lastGamePlayed = rosterDataService.lastGamePlayedFor(candidate.mlbamId, candidate.fullName)
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
            lastGamePlayed = lastGamePlayed,
        )
    }

    fun getDailyPlayerResponse(date: LocalDate): Map<String, Any> {
        val player =
            dailyPlayerRepository.findByGameDate(date)
                ?: throw IllegalStateException("No player available for $date")
        val response =
            mutableMapOf(
                "date" to date.toString(),
                "playerType" to if (player.isPitcher) "PITCHER" else "BATTER",
            )
        if (player.lastGamePlayed != null) response["lastGamePlayed"] = player.lastGamePlayed.toString()
        return response
    }

    fun getScreenshot(date: LocalDate): ByteArray? = dailyPlayerRepository.findByGameDate(date)?.screenshot

    fun getPlayerList(): List<Map<String, Any>> {
        val today = LocalDate.now()
        val combined = LinkedHashMap<String, Map<String, Any>>()
        val todaysPlayerMlbamId = dailyPlayerRepository.findByGameDate(today)?.mlbamId

        for (player in rosterDataService.players()) {
            val normalized = PlayerUtils.normalizeForSearch(player.fullName)
            val types =
                when {
                    player.position == "TWP" -> listOf("PITCHER", "BATTER")
                    player.position in PITCHER_POSITIONS -> listOf("PITCHER")
                    else -> listOf("BATTER")
                }
            val eligible = player.mlbamId == todaysPlayerMlbamId || rosterDataService.isPlayerEligible(player)
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
                    entry["eligibilityReason"] = rosterDataService.eligibilityReason(player, type == "PITCHER")
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
            result["hints"] = PlayerUtils.buildHints(player.toSnapshot(), guessNumber, playerName, rosterDataService.players())
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
