package com.savantle.backend.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class AlertService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username}") private val mailUsername: String,
    @Value("\${savantle.alert.email:\${spring.mail.username}}") private val alertEmail: String,
) {
    private val log = LoggerFactory.getLogger(AlertService::class.java)

    fun send(
        subject: String,
        body: String,
    ) {
        try {
            val mail = SimpleMailMessage()
            mail.setTo(alertEmail)
            mail.setFrom(mailUsername)
            mail.subject = "[Savantle Alert] $subject"
            mail.text = body
            mailSender.send(mail)
            log.info("Alert email sent: $subject")
        } catch (e: Exception) {
            log.error("Failed to send alert email: $subject", e)
        }
    }
}
