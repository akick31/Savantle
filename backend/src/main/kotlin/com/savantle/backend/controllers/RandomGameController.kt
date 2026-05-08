package com.savantle.backend.controllers

import com.savantle.backend.model.dto.RandomGuessRequest
import com.savantle.backend.services.RandomGameResponseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${api.base-path}/random-player")
class RandomGameController(private val randomGameResponseService: RandomGameResponseService) {
    @PostMapping("/new")
    fun newRandomGame(): ResponseEntity<Any> = randomGameResponseService.createRandomGame()

    @GetMapping("/screenshot/{gameId}")
    fun getRandomGameScreenshot(
        @PathVariable gameId: String,
    ): ResponseEntity<ByteArray> = randomGameResponseService.getRandomGameScreenshot(gameId)

    @PostMapping("/guess")
    fun randomGuess(
        @RequestBody request: RandomGuessRequest,
    ): ResponseEntity<Any> = randomGameResponseService.validateRandomGuess(request.gameId, request.playerName, request.guessNumber)
}
