package com.savantle.backend.services

import com.savantle.backend.model.dto.ContactRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class ContactService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username}") private val mailUsername: String,
) {
    private val log = LoggerFactory.getLogger(ContactService::class.java)

    companion object {
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }

    fun sendResponse(request: ContactRequest): org.springframework.http.ResponseEntity<Any> {
        return try {
            send(request)
            org.springframework.http.ResponseEntity.ok(mapOf("success" to true))
        } catch (e: IllegalArgumentException) {
            org.springframework.http.ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            org.springframework.http.ResponseEntity.internalServerError()
                .body(mapOf("error" to "Failed to send message. Please try again later."))
        }
    }

    fun send(request: ContactRequest) {
        val name = request.name.trim().take(100)
        val email = request.email.trim().take(200)
        val subject = request.subject.trim().take(200)
        val message = request.message.trim().take(5000)

        require(name.isNotBlank()) { "Name is required" }
        require(email.isNotBlank() && EMAIL_REGEX.matches(email)) { "Valid email is required" }
        require(subject.isNotBlank()) { "Subject is required" }
        require(message.isNotBlank()) { "Message is required" }

        val mail = SimpleMailMessage()
        mail.setTo(mailUsername)
        mail.setFrom(mailUsername)
        mail.replyTo = email
        mail.subject = "[Savantle] $subject"
        mail.text =
            """
            From: $name <$email>

            $message
            """.trimIndent()

        mailSender.send(mail)
        log.info("Contact email sent from $email — subject: $subject")
    }
}
