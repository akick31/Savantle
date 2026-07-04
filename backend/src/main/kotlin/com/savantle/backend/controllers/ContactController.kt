package com.savantle.backend.controllers

import com.savantle.backend.model.dto.request.ContactRequest
import com.savantle.backend.services.ContactService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${api.base-path}/contact")
class ContactController(private val contactService: ContactService) {
    @PostMapping
    fun contact(
        @RequestBody request: ContactRequest,
    ): ResponseEntity<Any> = contactService.sendResponse(request)
}
