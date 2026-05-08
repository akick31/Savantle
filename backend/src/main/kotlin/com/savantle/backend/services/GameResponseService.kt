package com.savantle.backend.services

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class GameResponseService(private val dailyPlayerService: DailyPlayerService) {
    companion object {
        private val EARLIEST_DATE: LocalDate = LocalDate.of(2025, 1, 1)
        private const val MAX_PLAYER_NAME_LENGTH = 100
    }

    fun getDailyPlayer(date: String?): ResponseEntity<Any> {
        return try {
            val targetDate = if (date != null) parseAndValidateDate(date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.getDailyPlayerResponse(targetDate))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid date")))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to "Daily player not yet available, please try again shortly."))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load daily player"))
        }
    }

    fun getScreenshot(date: String): ResponseEntity<ByteArray> {
        return try {
            val target = parseAndValidateDate(date)
            val bytes = dailyPlayerService.getScreenshot(target) ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(bytes)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    fun getLiveScreenshot(date: String): ResponseEntity<ByteArray> {
        return try {
            val target = parseAndValidateDate(date)
            val bytes = dailyPlayerService.getLiveScreenshot(target) ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    fun getPlayers(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(dailyPlayerService.getPlayerList())
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load player list"))
        }
    }

    fun validateGuess(
        playerName: String,
        date: String?,
        guessNumber: Int,
    ): ResponseEntity<Any> {
        if (playerName.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "Player name is required"))
        if (playerName.length > MAX_PLAYER_NAME_LENGTH) return ResponseEntity.badRequest().body(mapOf("error" to "Player name too long"))
        if (guessNumber < 1 || guessNumber > 5) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid guess number"))
        return try {
            val targetDate = if (date != null) parseAndValidateDate(date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.validateGuess(playerName, targetDate, guessNumber))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid date")))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to process guess"))
        }
    }

    fun getAvailableDates(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(dailyPlayerService.getAvailableDates())
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load available dates"))
        }
    }

    fun getRandomPastDate(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(mapOf("date" to dailyPlayerService.getRandomPastDate()))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to fetch random date"))
        }
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

    private fun parseAndValidateDate(date: String): LocalDate {
        val parsed =
            runCatching { LocalDate.parse(date) }
                .getOrElse { throw IllegalArgumentException("Invalid date format: $date") }
        require(parsed >= EARLIEST_DATE) { "Date is before the earliest available game" }
        require(parsed <= LocalDate.now()) { "Date is in the future" }
        return parsed
    }
}
