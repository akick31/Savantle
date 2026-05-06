package com.savantle.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "analytics",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_date", "event_type"])]
)
class Analytics(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "event_date", nullable = false)
    var eventDate: LocalDate,

    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,

    @Column(nullable = false)
    var count: Long = 0
)
