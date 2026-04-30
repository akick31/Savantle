package com.savantle.backend.services

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GameResponseService(private val dailyPlayerService: DailyPlayerService) {

    fun getDailyPlayer(date: String?): ResponseEntity<Any> {
        return try {
            val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.getDailyPlayerResponse(targetDate))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to "Daily player not yet available, please try again shortly."))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load daily player"))
        }
    }

    fun getScreenshot(date: String): ResponseEntity<ByteArray> {
        return try {
            val target = LocalDate.parse(date)
            val bytes = dailyPlayerService.getScreenshot(target)
                ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(bytes)
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

    fun validateGuess(playerName: String, date: String?, guessNumber: Int): ResponseEntity<Any> {
        if (playerName.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Player name is required"))
        }
        if (guessNumber < 1 || guessNumber > 5) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid guess number"))
        }
        return try {
            val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.validateGuess(playerName, targetDate, guessNumber))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to process guess"))
        }
    }

    fun curatePlayerForDate(date: String, playerName: String): ResponseEntity<Any> {
        if (date.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Date is required"))
        }
        if (playerName.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Player name is required"))
        }
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
}
