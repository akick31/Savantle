package com.savantle.backend.controllers

import com.savantle.backend.model.dto.GuessRequest
import com.savantle.backend.model.dto.ManualCurateRequest
import com.savantle.backend.services.GameResponseService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${api.base-path}")
class GameController(private val gameResponseService: GameResponseService) {
    @GetMapping("/daily")
    fun getDailyPlayer(
        @RequestParam(required = false) date: String?,
    ): ResponseEntity<Any> = gameResponseService.getDailyPlayer(date)

    @GetMapping("/screenshot/{date}")
    fun getScreenshot(
        @PathVariable date: String,
    ): ResponseEntity<ByteArray> {
        val response = gameResponseService.getScreenshot(date)
        if (response.statusCode.is2xxSuccessful && response.body != null) {
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

    @GetMapping("/available-dates")
    fun getAvailableDates(): ResponseEntity<Any> = gameResponseService.getAvailableDates()

    @GetMapping("/random-date")
    fun getRandomDate(): ResponseEntity<Any> = gameResponseService.getRandomPastDate()

    @PostMapping("/admin/curate")
    fun curateDate(
        @RequestBody request: ManualCurateRequest,
    ): ResponseEntity<Any> = gameResponseService.curateAutoForDate(request.date, request.playerName)
}
