package com.savantle.backend.controllers

import com.savantle.backend.model.dto.AnalyticsRequest
import com.savantle.backend.services.AnalyticsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${api.base-path}")
class AnalyticsController(private val analyticsService: AnalyticsService) {
    @PostMapping("/analytics")
    fun recordAnalytics(
        @RequestBody request: AnalyticsRequest,
    ): ResponseEntity<Any> = analyticsService.recordFromRequest(request)

    @GetMapping("/stats")
    fun getGlobalStats(): ResponseEntity<Any> = analyticsService.getGlobalStats()

    @GetMapping("/admin/analytics")
    fun getAnalytics(
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<Any> = analyticsService.getSummaryResponse(days)
}
