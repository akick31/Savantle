package com.savantle.backend.services

import com.savantle.backend.util.DateUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class ScreenshotResponseService(private val dailyPlayerService: DailyPlayerService) {
    fun getScreenshot(date: String): ResponseEntity<ByteArray> {
        return try {
            val target = DateUtils.parseAndValidateDate(date)
            val bytes = dailyPlayerService.getScreenshot(target) ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(bytes)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    fun getLiveScreenshot(date: String): ResponseEntity<ByteArray> {
        return try {
            val target = DateUtils.parseAndValidateDate(date)
            val bytes = dailyPlayerService.getLiveScreenshot(target) ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }
}
