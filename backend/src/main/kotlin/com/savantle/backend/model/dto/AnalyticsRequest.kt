package com.savantle.backend.model.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class AnalyticsRequest(
    @JsonProperty("eventType") val eventType: String,
)
