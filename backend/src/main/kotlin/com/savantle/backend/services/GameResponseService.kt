package com.savantle.backend.services

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDate

private val EARLIEST_DATE: LocalDate = LocalDate.of(2025, 1, 1)

@Service
class GameResponseService(private val dailyPlayerService: DailyPlayerService) {
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
            val bytes =
                dailyPlayerService.getScreenshot(target)
                    ?: return ResponseEntity.notFound().build()
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
            val bytes =
                dailyPlayerService.getLiveScreenshot(target)
                    ?: return ResponseEntity.notFound().build()
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

    fun createRandomGame(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(dailyPlayerService.createRandomGame())
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to create random game"))
        }
    }

    fun getRandomGameScreenshot(gameId: String): ResponseEntity<ByteArray> {
        return try {
            val bytes = dailyPlayerService.getRandomGameScreenshot(gameId)
            ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    fun validateRandomGuess(
        gameId: String,
        playerName: String,
        guessNumber: Int,
    ): ResponseEntity<Any> {
        if (playerName.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "Player name required"))
        if (guessNumber < 1 || guessNumber > 5) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid guess number"))
        return try {
            ResponseEntity.ok(dailyPlayerService.validateRandomGuess(gameId, playerName, guessNumber))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
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

    fun curatePlayerForDate(
        date: String,
        playerName: String,
    ): ResponseEntity<Any> {
        if (date.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "Date is required"))
        if (playerName.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "Player name is required"))
        return try {
            val targetDate = LocalDate.parse(date)
            ResponseEntity.ok(dailyPlayerService.curateSpecificPlayerForDate(targetDate, playerName))
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
        require(parsed <= LocalDate.now().plusDays(8)) { "Date is in the future" }
        return parsed
    }
}
