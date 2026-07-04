package com.savantle.backend.services

import com.savantle.backend.util.DateUtils
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GameResponseService(private val dailyPlayerService: DailyPlayerService) {
    companion object {
        private const val MAX_PLAYER_NAME_LENGTH = 100
    }

    fun getDailyPlayer(date: String?): ResponseEntity<Any> {
        return try {
            val targetDate = if (date != null) DateUtils.parseAndValidateDate(date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.getDailyPlayerResponse(targetDate))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid date")))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to "Daily player not yet available, please try again shortly."))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load daily player"))
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
            val targetDate = if (date != null) DateUtils.parseAndValidateDate(date) else LocalDate.now()
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
}
