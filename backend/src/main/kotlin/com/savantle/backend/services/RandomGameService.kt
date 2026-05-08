package com.savantle.backend.services

import com.savantle.backend.model.RandomGame
import com.savantle.backend.repositories.DailyPlayerRepository
import com.savantle.backend.util.PlayerUtils
import com.savantle.backend.util.PlayerUtils.PITCHER_POSITIONS
import com.savantle.backend.util.toSnapshot
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

@Service
class RandomGameService(
    private val screenshotService: ScreenshotService,
    private val dailyPlayerRepository: DailyPlayerRepository,
    private val dailyPlayerService: DailyPlayerService,
    @Value("\${savantle.curator.days-ahead:7}") private val daysAhead: Int,
) {
    companion object {
        private const val RANDOM_GAME_TTL_HOURS = 6L
        private const val MAX_SCREENSHOT_ATTEMPTS = 25
        private const val EXCLUDE_RECENT_DAYS = 30L
        private const val POOL_TARGET_SIZE = 3
    }

    private val log = LoggerFactory.getLogger(RandomGameService::class.java)
    private val randomGames = ConcurrentHashMap<String, RandomGame>()
    private val gamePool = LinkedBlockingQueue<RandomGame>()
    private val poolExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "random-pool-filler").also { it.isDaemon = true }
        }

    @EventListener(ApplicationReadyEvent::class)
    fun warmPool() {
        repeat(POOL_TARGET_SIZE) { poolExecutor.submit { tryFillPool() } }
    }

    private fun recentDailyMlbamIds(): Set<Int> {
        val today = LocalDate.now()
        return dailyPlayerRepository
            .findByGameDateBetween(today.minusDays(EXCLUDE_RECENT_DAYS), today)
            .map { it.mlbamId }
            .toSet()
    }

    private fun upcomingDailyMlbamIds(): Set<Int> {
        val today = LocalDate.now()
        return dailyPlayerRepository
            .findByGameDateBetween(today, today.plusDays(daysAhead.toLong()))
            .map { it.mlbamId }
            .toSet()
    }

    fun createRandomGame(): Map<String, Any> {
        pruneExpiredGames()

        val game =
            gamePool.poll() ?: run {
                log.info("Pool empty — capturing synchronously")
                captureOneGame() ?: throw IllegalStateException("Could not capture screenshot for any player, please try again")
            }

        randomGames[game.gameId] = game
        log.info("Random game served: ${game.fullName} (${game.gameId}), pool remaining: ${gamePool.size}")

        poolExecutor.submit { tryFillPool() }

        return mapOf("gameId" to game.gameId, "playerType" to if (game.isPitcher) "PITCHER" else "BATTER")
    }

    private fun tryFillPool() {
        if (gamePool.size >= POOL_TARGET_SIZE) return
        if (dailyPlayerService.getRosterCache().isEmpty()) {
            log.info("Roster not ready yet — retrying pool fill in 30s")
            poolExecutor.submit {
                Thread.sleep(30_000)
                tryFillPool()
            }
            return
        }
        val game = captureOneGame() ?: return
        gamePool.offer(game)
        log.info("Pool replenished: ${game.fullName}, pool size: ${gamePool.size}")
    }

    private fun captureOneGame(): RandomGame? {
        val excludedIds = recentDailyMlbamIds() + upcomingDailyMlbamIds() + gamePool.map { it.mlbamId }.toSet()
        val pool = dailyPlayerService.buildRandomPool(excludedIds)

        if (pool.isEmpty()) {
            if (dailyPlayerService.getRosterCache().isEmpty()) {
                log.warn("No eligible players for random game — roster not loaded yet")
            } else {
                log.warn("No eligible players for random game")
            }
            return null
        }

        for (candidate in pool.shuffled().take(MAX_SCREENSHOT_ATTEMPTS)) {
            val isPitcher = candidate.position in PITCHER_POSITIONS
            val result =
                screenshotService.capturePercentiles(candidate.mlbamId, candidate.fullName, isPitcher)
                    ?: continue

            val gameId = UUID.randomUUID().toString()
            return RandomGame(
                gameId = gameId,
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

        log.warn("Random game: no screenshot after $MAX_SCREENSHOT_ATTEMPTS tries (pool ${pool.size})")
        return null
    }

    fun getScreenshot(gameId: String): ByteArray = randomGames[gameId]?.screenshot ?: throw IllegalArgumentException("Game not found or expired")

    fun validateGuess(
        gameId: String,
        playerName: String,
        guessNumber: Int,
    ): Map<String, Any> {
        val game = randomGames[gameId] ?: throw IllegalArgumentException("Game not found or expired")
        val correct = PlayerUtils.normalizeForSearch(playerName) == game.normalizedName

        if (correct) {
            return mapOf("correct" to true, "playerInfo" to PlayerUtils.buildPlayerInfo(game.toSnapshot()))
        }

        val gameOver = guessNumber >= 5
        val result = mutableMapOf<String, Any>("correct" to false, "gameOver" to gameOver)
        if (gameOver) {
            result["playerInfo"] = PlayerUtils.buildPlayerInfo(game.toSnapshot())
        } else {
            result["hints"] = PlayerUtils.buildHints(game.toSnapshot(), guessNumber, playerName, dailyPlayerService.getRosterCache())
        }
        return result
    }

    private fun pruneExpiredGames() {
        val cutoff = Instant.now().minusSeconds(RANDOM_GAME_TTL_HOURS * 3600)
        randomGames.entries.removeIf { it.value.createdAt.isBefore(cutoff) }
    }
}
