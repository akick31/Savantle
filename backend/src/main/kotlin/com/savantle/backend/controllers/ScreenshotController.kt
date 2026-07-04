package com.savantle.backend.controllers

import com.savantle.backend.services.ScreenshotResponseService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${api.base-path}/screenshot")
class ScreenshotController(private val screenshotResponseService: ScreenshotResponseService) {
    @GetMapping("/{date}")
    fun getScreenshot(
        @PathVariable date: String,
    ): ResponseEntity<ByteArray> {
        val response = screenshotResponseService.getScreenshot(date)
        if (response.statusCode.is2xxSuccessful && response.body != null) {
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(response.body)
        }
        return response
    }

    @GetMapping("/live/{date}")
    fun getLiveScreenshot(
        @PathVariable date: String,
    ): ResponseEntity<ByteArray> = screenshotResponseService.getLiveScreenshot(date)
}
