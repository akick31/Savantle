package com.savantle.backend.controllers

import com.savantle.backend.services.DailyPlayerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/health")
class HealthController(private val dailyPlayerService: DailyPlayerService) {
    @GetMapping
    fun health() =
        mapOf(
            "status" to "UP",
            "playersLoaded" to dailyPlayerService.isReady(),
        )
}
