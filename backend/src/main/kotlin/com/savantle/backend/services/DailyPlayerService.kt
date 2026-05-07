package com.savantle.backend.services

import com.savantle.backend.model.DailyPlayer
import com.savantle.backend.model.MLBPlayer
import com.savantle.backend.model.RandomGame
import com.savantle.backend.model.ScreenshotResult
import com.savantle.backend.repositories.DailyPlayerRepository
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
class DailyPlayerService(
    private val mlbRosterService: MLBRosterService,
    private val screenshotService: ScreenshotService,
    private val repository: DailyPlayerRepository,
    private val entityManager: EntityManager,
    @Value("\${savantle.curator.days-ahead:7}") private val daysAhead: Int,
    @Value("\${savantle.qualification.batter-min-pa:75}") private val minBatterPa: Int,
    @Value("\${savantle.qualification.pitcher-min-ip:15.0}") private val minPitcherIp: Double,
    @Value("\${savantle.qualification.early-season-days:30}") private val earlySeasonDays: Long,
) {
    companion object {
        val PITCHER_POSITIONS = setOf("SP", "RP", "P")
        private const val RANDOM_GAME_TTL_HOURS = 6L
        private const val LIVE_SCREENSHOT_TTL_HOURS = 24L
        private const val MAX_RANDOM_SCREENSHOT_ATTEMPTS = 25
        /** Exclude today's daily and recent Savantle answers from random mode. */
        private const val RANDOM_EXCLUDE_RECENT_DAYS = 30L
    }

    private val randomGames = ConcurrentHashMap<String, RandomGame>()

    // Cache for live (replay) screenshots: date string → (bytes, capturedAt)
    private data class CachedScreenshot(val bytes: ByteArray, val capturedAt: Instant)

    private val liveScreenshotCache = ConcurrentHashMap<String, CachedScreenshot>()

    private val log = LoggerFactory.getLogger(DailyPlayerService::class.java)

    @Volatile private var rosterCache: List<MLBPlayer> = emptyList()

    @Volatile private var rosterCacheDate: LocalDate? = null

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

    @Scheduled(cron = "0 10 4 * * *")
    @Transactional
    fun refreshTodayScreenshot() {
        val today = LocalDate.now()
        val player = repository.findByGameDate(today) ?: return
        val result = screenshotService.capturePercentiles(player.mlbamId, player.fullName, player.isPitcher)
        if (result != null) {
            player.screenshot = result.pngBytes
            player.savantUrl = result.savantUrl
            repository.save(player)
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

    private fun isEarlySeason(date: LocalDate): Boolean {
        val start = seasonStartDate ?: return true
        return date < start.plusDays(earlySeasonDays)
    }

    @Transactional
    fun curateUpcomingDays() {
        val futureDate = LocalDate.now().plusDays(daysAhead.toLong())
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
    fun curateSpecificPlayerForDate(
        date: LocalDate,
        playerName: String,
    ): Map<String, Any> {
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
        val candidate =
            players.firstOrNull { normalizeForSearch(it.fullName) == normalized }
                ?: throw IllegalArgumentException("Player not found in current MLB roster: $playerName")

        val isPitcher = candidate.position in PITCHER_POSITIONS
        val result =
            screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
                ?: throw IllegalStateException("Could not capture screenshot for ${candidate.fullName}")

        repository.deleteByGameDate(date)
        entityManager.flush()
        val saved =
            repository.save(
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
                    screenshot = result.pngBytes,
                ),
            )
        log.info("Manually curated $date: ${saved.fullName}")

        return mapOf(
            "date" to saved.gameDate.toString(),
            "fullName" to saved.fullName,
            "position" to formatPosition(saved),
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
            repository
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
                normalizedName = normalizeForSearch(candidate.fullName),
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
            repository.findByGameDate(date)
                ?: throw IllegalStateException("No player available for $date")
        return mapOf(
            "date" to date.toString(),
            "playerType" to if (player.isPitcher) "PITCHER" else "BATTER",
        )
    }

    fun getScreenshot(date: LocalDate): ByteArray? {
        return repository.findByGameDate(date)?.screenshot
    }

    fun getPlayerList(): List<Map<String, String>> {
        val today = LocalDate.now()

        // Key: normalizedName|playerType -> entry (allows same player as both PITCHER and BATTER for TWP)
        val combined = LinkedHashMap<String, Map<String, String>>()

        // Roster cache — TWP players get two entries
        for (player in rosterCache) {
            val normalized = normalizeForSearch(player.fullName)
            val types =
                if (player.position == "TWP") {
                    listOf("PITCHER", "BATTER")
                } else if (player.position in PITCHER_POSITIONS) {
                    listOf("PITCHER")
                } else {
                    listOf("BATTER")
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

        // All curated players (past, today, upcoming) — always include, override roster if name matches
        val allCurated = repository.findByGameDateBetween(LocalDate.of(2000, 1, 1), today.plusDays(daysAhead.toLong()))
        for (player in allCurated) {
            val normalized = normalizeForSearch(player.fullName)
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
            repository.findByGameDate(date)
                ?: throw IllegalStateException("No player available for $date")
        val correct = normalizeForSearch(playerName) == player.normalizedName

        if (correct) {
            return mapOf(
                "correct" to true,
                "playerInfo" to buildPlayerInfo(player),
            )
        }

        val gameOver = guessNumber >= 5
        val result =
            mutableMapOf<String, Any>(
                "correct" to false,
                "gameOver" to gameOver,
            )
        if (gameOver) {
            result["playerInfo"] = buildPlayerInfo(player)
        } else {
            result["hints"] = buildHints(player, guessNumber, playerName)
        }
        return result
    }

    private fun buildHints(
        player: DailyPlayer,
        guessNumber: Int,
        guessedPlayerName: String,
    ): List<Map<String, Any>> {
        val hints = mutableListOf<Map<String, Any>>()
        val confirmedTypes = mutableSetOf<String>()

        val guessedPlayer =
            rosterCache.firstOrNull {
                normalizeForSearch(it.fullName) == normalizeForSearch(guessedPlayerName)
            }

        if (guessedPlayer != null) {
            when {
                guessedPlayer.team.name == player.teamName -> {
                    hints += hint("TEAM", "Team", player.teamName, confirmed = true)
                    hints += hint("DIVISION", "Division", player.division, confirmed = true)
                    hints += hint("LEAGUE", "League", player.league, confirmed = true)
                    confirmedTypes += setOf("TEAM", "DIVISION", "LEAGUE")
                }
                guessedPlayer.team.division == player.division -> {
                    hints += hint("DIVISION", "Division", player.division, confirmed = true)
                    hints += hint("LEAGUE", "League", player.league, confirmed = true)
                    confirmedTypes += setOf("DIVISION", "LEAGUE")
                }
                // League-only match is intentionally not confirmed — revealing it early lets
                // players deduce the opposite league by absence.
            }
            // Position/handedness is not confirmed early for the same reason.
        }

        val scheduledType =
            when (guessNumber) {
                1 -> "POSITION"
                2 -> "LEAGUE"
                3 -> "DIVISION"
                4 -> "TEAM"
                else -> null
            }

        if (scheduledType != null && scheduledType !in confirmedTypes) {
            hints +=
                when (scheduledType) {
                    "POSITION" -> hint("POSITION", "Position", formatPosition(player), confirmed = false)
                    "LEAGUE" -> hint("LEAGUE", "League", player.league, confirmed = false)
                    "DIVISION" -> hint("DIVISION", "Division", player.division, confirmed = false)
                    "TEAM" -> hint("TEAM", "Team", player.teamName, confirmed = false)
                    else -> return hints
                }
        }

        return hints
    }

    private fun hint(
        type: String,
        label: String,
        value: String,
        confirmed: Boolean,
    ): Map<String, Any> = mapOf("type" to type, "label" to label, "value" to value, "confirmed" to confirmed)

    private fun buildPlayerInfo(player: DailyPlayer): Map<String, String> {
        return mapOf(
            "fullName" to player.fullName,
            "position" to formatPosition(player),
            "teamName" to player.teamName,
            "teamAbbr" to player.teamAbbr,
            "league" to player.league,
            "division" to player.division,
            "mlbamId" to player.mlbamId.toString(),
            "savantUrl" to player.savantUrl,
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

    private fun formatMLBPlayerPosition(player: MLBPlayer): String {
        if (player.position !in PITCHER_POSITIONS) return player.position
        return when (player.throwingHand?.uppercase()) {
            "L" -> "LHP"
            "R" -> "RHP"
            else -> "P"
        }
    }

    // ── Live screenshot (replay mode) ─────────────────────────────────────────

    fun getLiveScreenshot(date: LocalDate): ByteArray? {
        val dateKey = date.toString()
        val cached = liveScreenshotCache[dateKey]
        if (cached != null && Instant.now().isBefore(cached.capturedAt.plusSeconds(LIVE_SCREENSHOT_TTL_HOURS * 3600))) {
            log.info("Returning cached live screenshot for $dateKey")
            return cached.bytes
        }

        val player = repository.findByGameDate(date) ?: return null
        val result =
            screenshotService.capturePercentiles(player.mlbamId, player.fullName, player.isPitcher)
                ?: return null
        liveScreenshotCache[dateKey] = CachedScreenshot(result.pngBytes, Instant.now())
        return result.pngBytes
    }

    // ── Random player game (in-memory, never persisted) ───────────────────────

    private fun captureRandomCandidateScreenshot(candidate: MLBPlayer): Pair<ScreenshotResult, MLBPlayer>? {
        val isPitcher = candidate.position in PITCHER_POSITIONS
        val result =
            screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
                ?: return null
        return result to candidate
    }

    /** MLBAM IDs used as Savantle daily answers in the last [RANDOM_EXCLUDE_RECENT_DAYS] days (inclusive of today). */
    private fun recentDailySavantleMlbamIds(): Set<Int> {
        val today = LocalDate.now()
        val start = today.minusDays(RANDOM_EXCLUDE_RECENT_DAYS)
        return repository
            .findByGameDateBetween(start, today)
            .map { it.mlbamId }
            .toSet()
    }

    fun createRandomGame(): Map<String, Any> {
        pruneExpiredRandomGames()

        val excludedMlbamIds = recentDailySavantleMlbamIds()

        val basePool =
            if (!isEarlySeason(LocalDate.now()) && qualifiedIds.isNotEmpty()) {
                val filtered = rosterCache.filter { it.mlbamId in qualifiedIds }
                filtered.ifEmpty { rosterCache }
            } else {
                rosterCache
            }
        val pool = basePool.filter { it.mlbamId !in excludedMlbamIds }
        if (pool.isEmpty()) {
            throw IllegalStateException("No eligible players for random game right now, try again shortly")
        }

        val players = pool.shuffled()
        for (candidate in players.take(MAX_RANDOM_SCREENSHOT_ATTEMPTS)) {
            val got = captureRandomCandidateScreenshot(candidate) ?: continue
            val (result, picked) = got
            val isPitcher = picked.position in PITCHER_POSITIONS
            val gameId = UUID.randomUUID().toString()
            randomGames[gameId] =
                RandomGame(
                    gameId = gameId,
                    mlbamId = picked.mlbamId,
                    fullName = picked.fullName,
                    normalizedName = normalizeForSearch(picked.fullName),
                    position = picked.position,
                    throwingHand = picked.throwingHand,
                    isPitcher = isPitcher,
                    teamName = picked.team.name,
                    teamAbbr = picked.team.abbreviation,
                    league = picked.team.league,
                    division = picked.team.division,
                    savantUrl = result.savantUrl,
                    screenshot = result.pngBytes,
                )
            log.info("Random game created: ${picked.fullName} ($gameId)")
            return mapOf("gameId" to gameId, "playerType" to if (isPitcher) "PITCHER" else "BATTER")
        }
        log.warn(
            "Random game: no screenshot after $MAX_RANDOM_SCREENSHOT_ATTEMPTS tries " +
                "(pool size ${players.size}); try again later",
        )
        throw IllegalStateException("Could not capture screenshot for any player, please try again")
    }

    fun getRandomGameScreenshot(gameId: String): ByteArray {
        return randomGames[gameId]?.screenshot ?: throw IllegalArgumentException("Game not found or expired")
    }

    fun validateRandomGuess(
        gameId: String,
        playerName: String,
        guessNumber: Int,
    ): Map<String, Any> {
        val game = randomGames[gameId] ?: throw IllegalArgumentException("Game not found or expired")
        val correct = normalizeForSearch(playerName) == game.normalizedName

        if (correct) {
            return mapOf("correct" to true, "playerInfo" to buildRandomPlayerInfo(game))
        }

        val gameOver = guessNumber >= 5
        val result = mutableMapOf<String, Any>("correct" to false, "gameOver" to gameOver)
        if (gameOver) {
            result["playerInfo"] = buildRandomPlayerInfo(game)
        } else {
            result["hints"] = buildRandomHints(game, guessNumber, playerName)
        }
        return result
    }

    private fun buildRandomPlayerInfo(game: RandomGame): Map<String, String> =
        mapOf(
            "fullName" to game.fullName,
            "position" to formatRandomPosition(game),
            "teamName" to game.teamName,
            "teamAbbr" to game.teamAbbr,
            "league" to game.league,
            "division" to game.division,
            "mlbamId" to game.mlbamId.toString(),
            "savantUrl" to game.savantUrl,
        )

    private fun formatRandomPosition(game: RandomGame): String {
        if (!game.isPitcher) return game.position
        return when (game.throwingHand?.uppercase()) {
            "L" -> "LHP"
            "R" -> "RHP"
            else -> "P"
        }
    }

    private fun buildRandomHints(
        game: RandomGame,
        guessNumber: Int,
        guessedPlayerName: String,
    ): List<Map<String, Any>> {
        val hints = mutableListOf<Map<String, Any>>()
        val confirmedTypes = mutableSetOf<String>()

        val guessedPlayer =
            rosterCache.firstOrNull {
                normalizeForSearch(it.fullName) == normalizeForSearch(guessedPlayerName)
            }

        if (guessedPlayer != null) {
            when {
                guessedPlayer.team.name == game.teamName -> {
                    hints += hint("TEAM", "Team", game.teamName, confirmed = true)
                    hints += hint("DIVISION", "Division", game.division, confirmed = true)
                    hints += hint("LEAGUE", "League", game.league, confirmed = true)
                    confirmedTypes += setOf("TEAM", "DIVISION", "LEAGUE")
                }
                guessedPlayer.team.division == game.division -> {
                    hints += hint("DIVISION", "Division", game.division, confirmed = true)
                    hints += hint("LEAGUE", "League", game.league, confirmed = true)
                    confirmedTypes += setOf("DIVISION", "LEAGUE")
                }
            }
        }

        val scheduledType =
            when (guessNumber) {
                1 -> "POSITION"
                2 -> "LEAGUE"
                3 -> "DIVISION"
                4 -> "TEAM"
                else -> null
            }
        if (scheduledType != null && scheduledType !in confirmedTypes) {
            hints +=
                when (scheduledType) {
                    "POSITION" -> hint("POSITION", "Position", formatRandomPosition(game), confirmed = false)
                    "LEAGUE" -> hint("LEAGUE", "League", game.league, confirmed = false)
                    "DIVISION" -> hint("DIVISION", "Division", game.division, confirmed = false)
                    "TEAM" -> hint("TEAM", "Team", game.teamName, confirmed = false)
                    else -> return hints
                }
        }
        return hints
    }

    private fun pruneExpiredRandomGames() {
        val cutoff = Instant.now().minusSeconds(RANDOM_GAME_TTL_HOURS * 3600)
        randomGames.entries.removeIf { it.value.createdAt.isBefore(cutoff) }
    }

    fun getAvailableDates(): List<String> {
        val today = LocalDate.now()
        return repository.findByGameDateBetween(LocalDate.of(2020, 1, 1), today.minusDays(1))
            .map { it.gameDate.toString() }
            .sorted()
    }

    fun getRandomPastDate(): String {
        val dates = getAvailableDates()
        require(dates.isNotEmpty()) { "No past games available" }
        return dates.random()
    }

    fun isReady(): Boolean = repository.existsByGameDate(LocalDate.now())
}
