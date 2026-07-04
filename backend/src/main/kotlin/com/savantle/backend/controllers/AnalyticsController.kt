package com.savantle.backend.controllers

import com.savantle.backend.model.dto.request.AnalyticsRequest
import com.savantle.backend.services.AnalyticsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("\${api.base-path}/analytics")
class AnalyticsController(private val analyticsService: AnalyticsService) {
    @PostMapping
    fun recordAnalytics(
        @RequestBody request: AnalyticsRequest,
    ): ResponseEntity<Any> = analyticsService.recordFromRequest(request)

    @GetMapping
    fun getAnalytics(
        @RequestParam(defaultValue = "30") days: Int,
    ): ResponseEntity<Any> = analyticsService.getSummaryResponse(days)

    @GetMapping("/stats")
    fun getGlobalStats(
        @RequestParam(required = false) date: String?,
    ): ResponseEntity<Any> {
        val parsedDate = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return analyticsService.getGlobalStats(parsedDate ?: AnalyticsService.analyticsDate())
    }
}
