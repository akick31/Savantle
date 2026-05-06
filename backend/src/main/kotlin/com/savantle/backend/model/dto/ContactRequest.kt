package com.savantle.backend.model.dto

data class ContactRequest(
    val name: String,
    val email: String,
    val subject: String,
    val message: String,
)
