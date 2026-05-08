package com.savantle.backend.model.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ManualCurateRequest(
    @JsonProperty("date") val date: String,
    @JsonProperty("playerName") val playerName: String? = null,
)
