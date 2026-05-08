package com.savantle.backend.model.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Explicit JSON names so requests work when the app uses [spring.jackson.property-naming-strategy=SNAKE_CASE]
 * (prod): the frontend still sends camelCase, same as [GuessRequest].
 */
data class RandomGuessRequest(
    @JsonProperty("gameId") val gameId: String,
    @JsonProperty("playerName") val playerName: String,
    @JsonProperty("guessNumber") val guessNumber: Int,
)
