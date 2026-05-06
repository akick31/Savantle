package com.savantle.backend.controllers

import com.savantle.backend.model.dto.ContactRequest
import com.savantle.backend.model.dto.GuessRequest
import com.savantle.backend.model.dto.RandomGuessRequest
import com.savantle.backend.services.AnalyticsService
import com.savantle.backend.services.ContactService
import com.savantle.backend.services.GameResponseService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/savantle")
class GameController(
    private val gameResponseService: GameResponseService,
    private val contactService: ContactService,
    private val analyticsService: AnalyticsService,
) {
    @GetMapping("/daily")
    fun getDailyPlayer(
        @RequestParam(required = false) date: String?,
    ): ResponseEntity<Any> = gameResponseService.getDailyPlayer(date)

    @GetMapping("/screenshot/{date}")
    fun getScreenshot(
        @PathVariable date: String,
    ): ResponseEntity<ByteArray> {
        val response = gameResponseService.getScreenshot(date)
        if (response.statusCode?.is2xxSuccessful == true && response.body != null) {
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(response.body)
        }
        return response
    }

    @GetMapping("/screenshot/live/{date}")
    fun getLiveScreenshot(
        @PathVariable date: String,
    ): ResponseEntity<ByteArray> = gameResponseService.getLiveScreenshot(date)

    @GetMapping("/players")
    fun getPlayers(): ResponseEntity<Any> = gameResponseService.getPlayers()

    @PostMapping("/guess")
    fun makeGuess(
        @RequestBody request: GuessRequest,
    ): ResponseEntity<Any> = gameResponseService.validateGuess(request.playerName, request.date, request.guessNumber)

    @PostMapping("/random-player/new")
    fun newRandomGame(): ResponseEntity<Any> = gameResponseService.createRandomGame()

    @GetMapping("/random-player/screenshot/{gameId}")
    fun getRandomGameScreenshot(
        @PathVariable gameId: String,
    ): ResponseEntity<ByteArray> = gameResponseService.getRandomGameScreenshot(gameId)

    @PostMapping("/random-player/guess")
    fun randomGuess(
        @RequestBody request: RandomGuessRequest,
    ): ResponseEntity<Any> = gameResponseService.validateRandomGuess(request.gameId, request.playerName, request.guessNumber)

    @GetMapping("/available-dates")
    fun getAvailableDates(): ResponseEntity<Any> = gameResponseService.getAvailableDates()

    @GetMapping("/random-date")
    fun getRandomDate(): ResponseEntity<Any> = gameResponseService.getRandomPastDate()

    @PostMapping("/analytics")
    fun recordAnalytics(
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Any> = analyticsService.recordFromRequest(body)

    @GetMapping("/admin/analytics")
    fun getAnalytics(
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<Any> = analyticsService.getSummaryResponse(days)

    @PostMapping("/contact")
    fun contact(
        @RequestBody request: ContactRequest,
    ): ResponseEntity<Any> = contactService.sendResponse(request)
}
