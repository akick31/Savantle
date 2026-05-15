package com.savantle.backend.services

import com.savantle.backend.model.Analytics
import com.savantle.backend.model.dto.AnalyticsRequest
import com.savantle.backend.repositories.AnalyticsRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Service
class AnalyticsService(private val analyticsRepository: AnalyticsRepository) {
    private val log = LoggerFactory.getLogger(AnalyticsService::class.java)

    companion object {
        private val ANALYTICS_ZONE: ZoneId = ZoneId.of("America/New_York")

        fun analyticsDate(): LocalDate = ZonedDateTime.now(ANALYTICS_ZONE).toLocalDate()

        val ALLOWED_EVENTS =
            setOf(
                "UNIQUE_VISITORS", "GAME_WON", "GAME_LOST",
                "REPLAY_PLAYED", "RANDOM_PLAYED",
                "GUESS_1", "GUESS_2", "GUESS_3", "GUESS_4", "GUESS_5",
            )
    }

    @Transactional
    fun record(
        eventType: String,
        date: LocalDate = analyticsDate(),
    ) {
        require(eventType in ALLOWED_EVENTS) { "Unknown event type: $eventType" }
        val updated = analyticsRepository.increment(date, eventType)
        if (updated == 0) {
            try {
                analyticsRepository.save(Analytics(eventDate = date, eventType = eventType, count = 1))
            } catch (e: Exception) {
                analyticsRepository.increment(date, eventType)
            }
        }
        log.debug("Analytics recorded: $eventType for $date")
    }

    fun recordFromRequest(request: AnalyticsRequest): ResponseEntity<Any> {
        return try {
            val date = request.date?.let { LocalDate.parse(it) } ?: analyticsDate()
            record(request.eventType, date)
            ResponseEntity.ok(mapOf("ok" to true))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }
    }

    fun getSummary(
        start: LocalDate,
        end: LocalDate,
    ): List<Map<String, Any>> {
        return analyticsRepository.findByEventDateBetween(start, end)
            .sortedWith(compareBy({ it.eventDate }, { it.eventType }))
            .map { mapOf("date" to it.eventDate.toString(), "eventType" to it.eventType, "count" to it.count) }
    }

    fun getSummaryResponse(days: Int): ResponseEntity<Any> {
        val end = analyticsDate()
        val start = end.minusDays(days.toLong())
        return ResponseEntity.ok(getSummary(start, end))
    }

    fun getGlobalStats(date: LocalDate = analyticsDate()): ResponseEntity<Any> {
        val today = date
        val all = analyticsRepository.findByEventDateBetween(today, today)
        val byType = all.groupBy { it.eventType }.mapValues { (_, rows) -> rows.sumOf { it.count } }

        val wins = byType["GAME_WON"] ?: 0L
        val losses = byType["GAME_LOST"] ?: 0L
        val totalGames = wins + losses

        val guessDistribution =
            mapOf(
                "1" to (byType["GUESS_1"] ?: 0L),
                "2" to (byType["GUESS_2"] ?: 0L),
                "3" to (byType["GUESS_3"] ?: 0L),
                "4" to (byType["GUESS_4"] ?: 0L),
                "5" to (byType["GUESS_5"] ?: 0L),
            )

        val guessSum =
            1L * (byType["GUESS_1"] ?: 0L) +
                2L * (byType["GUESS_2"] ?: 0L) +
                3L * (byType["GUESS_3"] ?: 0L) +
                4L * (byType["GUESS_4"] ?: 0L) +
                5L * (byType["GUESS_5"] ?: 0L) +
                6L * losses

        val averageGuesses =
            if (totalGames > 0) {
                (guessSum.toDouble() / totalGames * 100).toLong() / 100.0
            } else {
                0.0
            }

        return ResponseEntity.ok(
            mapOf(
                "totalWins" to wins,
                "totalLosses" to losses,
                "guessDistribution" to guessDistribution,
                "averageGuesses" to averageGuesses,
            ),
        )
    }
}
