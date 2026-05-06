package com.savantle.backend.model.dto

data class RandomGuessRequest(
    val gameId: String,
    val playerName: String,
    val guessNumber: Int,
)
