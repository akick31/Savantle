package com.savantle.backend.controllers

import com.savantle.backend.services.DailyPlayerService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

data class GuessRequest(
    val playerName: String = "",
    val date: String? = null,
    val guessNumber: Int = 1
)

data class ManualCurateRequest(
    val date: String,
    val playerName: String
)

@RestController
@RequestMapping("/api/v1/savantle")
class GameController(private val dailyPlayerService: DailyPlayerService) {

    @GetMapping("/daily")
    fun getDailyPlayer(@RequestParam(required = false) date: String?): ResponseEntity<Any> {
        return try {
            val targetDate = if (date != null) LocalDate.parse(date) else LocalDate.now()
            ResponseEntity.ok(dailyPlayerService.getDailyPlayerResponse(targetDate))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to "Daily player not yet available, please try again shortly."))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to load daily player"))
        }
    }

    @GetMapping("/screenshot/{date}")
    fun getScreenshot(@PathVariable date: String): ResponseEntity<ByteArray> {
        return try {
            val target = LocalDate.parse(date)
            val bytes = dailyPlayerService.getScreenshot(target)
                ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(bytes)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
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

    @PostMapping("/admin/curate")
    fun curatePlayerForDate(@RequestBody request: ManualCurateRequest): ResponseEntity<Any> {
        if (request.date.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Date is required"))
        }
        if (request.playerName.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Player name is required"))
        }
        return try {
            val date = LocalDate.parse(request.date)
            ResponseEntity.ok(dailyPlayerService.curateSpecificPlayerForDate(date, request.playerName))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to (e.message ?: "Service unavailable")))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to curate player"))
        }
    }
}
