package com.savantle.backend.controllers

import com.savantle.backend.model.dto.GuessRequest
import com.savantle.backend.model.dto.ManualCurateRequest
import com.savantle.backend.services.GameResponseService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/savantle")
class GameController(private val gameResponseService: GameResponseService) {

    @GetMapping("/daily")
    fun getDailyPlayer(@RequestParam(required = false) date: String?): ResponseEntity<Any> {
        return gameResponseService.getDailyPlayer(date)
    }

    @GetMapping("/screenshot/{date}")
    fun getScreenshot(@PathVariable date: String): ResponseEntity<ByteArray> {
        val response = gameResponseService.getScreenshot(date)
        if (response.statusCode?.is2xxSuccessful == true && response.body != null) {
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(response.body)
        }
        return response
    }

    @GetMapping("/players")
    fun getPlayers(): ResponseEntity<Any> {
        return gameResponseService.getPlayers()
    }

    @PostMapping("/guess")
    fun makeGuess(@RequestBody request: GuessRequest): ResponseEntity<Any> {
        return gameResponseService.validateGuess(request.playerName, request.date, request.guessNumber)
    }

    @PostMapping("/admin/curate")
    fun curatePlayerForDate(@RequestBody request: ManualCurateRequest): ResponseEntity<Any> {
        return gameResponseService.curatePlayerForDate(request.date, request.playerName)
    }
}
