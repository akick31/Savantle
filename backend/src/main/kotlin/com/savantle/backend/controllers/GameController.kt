package com.savantle.backend.controllers

import com.savantle.backend.model.dto.GuessRequest
import com.savantle.backend.services.GameResponseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${api.base-path}/game")
class GameController(private val gameResponseService: GameResponseService) {
    @GetMapping("/daily")
    fun getDailyPlayer(
        @RequestParam(required = false) date: String?,
    ): ResponseEntity<Any> = gameResponseService.getDailyPlayer(date)

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
}
