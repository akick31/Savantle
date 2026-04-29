package com.savantle.backend.controllers

import com.savantle.backend.services.DailyPlayerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val dailyPlayerService: DailyPlayerService) {

    @GetMapping("/health")
    fun health() = mapOf(
        "status" to "UP",
        "playersLoaded" to dailyPlayerService.isReady()
    )
}
