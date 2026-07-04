package com.savantle.backend.services

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class AdminService(private val dailyPlayerService: DailyPlayerService) {
    companion object {
        private const val MAX_PLAYER_NAME_LENGTH = 100
    }

    fun curateAutoForDate(
        date: String,
        playerName: String? = null,
    ): ResponseEntity<Any> {
        if (date.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "Date is required"))
        if (playerName != null && playerName.length > MAX_PLAYER_NAME_LENGTH) return ResponseEntity.badRequest().body(mapOf("error" to "Player name too long"))
        return try {
            val targetDate = LocalDate.parse(date)
            val result =
                if (playerName.isNullOrBlank()) {
                    dailyPlayerService.curateAutoForDate(targetDate)
                } else {
                    dailyPlayerService.curateSpecificPlayerForDate(targetDate, playerName)
                }
            ResponseEntity.ok(result)
        } catch (e: DateTimeParseException) {
            ResponseEntity.badRequest().body(mapOf("error" to "Invalid date format: $date"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to (e.message ?: "Service unavailable")))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to curate player"))
        }
    }
}
