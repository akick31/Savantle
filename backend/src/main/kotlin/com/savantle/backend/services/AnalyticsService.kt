package com.savantle.backend.services

import com.savantle.backend.model.Analytics
import com.savantle.backend.repositories.AnalyticsRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class AnalyticsService(private val repository: AnalyticsRepository) {
    private val log = LoggerFactory.getLogger(AnalyticsService::class.java)

    companion object {
        val ALLOWED_EVENTS =
            setOf(
                "UNIQUE_VISITORS", "GAME_WON", "GAME_LOST",
                "REPLAY_PLAYED", "RANDOM_PLAYED",
                "GUESS_1", "GUESS_2", "GUESS_3", "GUESS_4", "GUESS_5",
            )
    }

    @Transactional
    fun record(eventType: String) {
        require(eventType in ALLOWED_EVENTS) { "Unknown event type: $eventType" }
        val today = LocalDate.now()
        val updated = repository.increment(today, eventType)
        if (updated == 0) {
            try {
                repository.save(Analytics(eventDate = today, eventType = eventType, count = 1))
            } catch (e: Exception) {
                repository.increment(today, eventType)
            }
        }
        log.debug("Analytics recorded: $eventType")
    }

    fun recordFromRequest(body: Map<String, String>): ResponseEntity<Any> {
        return try {
            val eventType =
                body["eventType"]
                    ?: return ResponseEntity.badRequest().body(mapOf("error" to "eventType required"))
            record(eventType)
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
        return repository.findByEventDateBetween(start, end)
            .sortedWith(compareBy({ it.eventDate }, { it.eventType }))
            .map { mapOf("date" to it.eventDate.toString(), "eventType" to it.eventType, "count" to it.count) }
    }

    fun getSummaryResponse(days: Int): ResponseEntity<Any> {
        val end = LocalDate.now()
        val start = end.minusDays(days.toLong())
        return ResponseEntity.ok(getSummary(start, end))
    }
}
