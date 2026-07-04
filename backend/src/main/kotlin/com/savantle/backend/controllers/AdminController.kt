package com.savantle.backend.controllers

import com.savantle.backend.model.dto.request.ManualCurateRequest
import com.savantle.backend.services.AdminService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${api.base-path}/admin")
class AdminController(private val adminService: AdminService) {
    @PostMapping("/curate")
    fun curateDate(
        @RequestBody request: ManualCurateRequest,
    ): ResponseEntity<Any> = adminService.curateAutoForDate(request.date, request.playerName)
}
