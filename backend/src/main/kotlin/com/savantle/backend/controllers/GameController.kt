package com.savantle.backend.controllers

import com.savantle.backend.services.DailyPlayerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

data class GuessRequest(
    val playerName: String = "",
    val date: String? = null,
    val guessNumber: Int = 1
)

@RestController
@RequestMapping("/api/v1/savantle")
class GameController(private val dailyPlayerService: DailyPlayerService) {

    @GetMapping("/daily")
    fun getDailyPlayer(@RequestParam(required = false) date: String?): ResponseEntity<Any> {
        return try {
            if (!dailyPlayerService.isReady()) {
                return ResponseEntity.status(503).body(mapOf("error" to "Server is still loading player data, please try again shortly."))
            }
            val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.getDailyPlayerResponse(targetDate))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load daily player"))
        }
    }

    @GetMapping("/players")
    fun getPlayers(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(dailyPlayerService.getPlayerList())
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load player list"))
        }
    }

    @PostMapping("/guess")
    fun makeGuess(@RequestBody request: GuessRequest): ResponseEntity<Any> {
        if (request.playerName.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Player name is required"))
        }
        if (request.guessNumber < 1 || request.guessNumber > 5) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid guess number"))
        }
        return try {
            val date = if (request.date != null) LocalDate.parse(request.date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.validateGuess(request.playerName, date, request.guessNumber))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to process guess"))
        }
    }
}
