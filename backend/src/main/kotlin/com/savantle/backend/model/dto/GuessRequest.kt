package com.savantle.backend.model.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GuessRequest(
    @JsonProperty("playerName") val playerName: String,
    @JsonProperty("date") val date: String?,
    @JsonProperty("guessNumber") val guessNumber: Int
)
