package com.savantle.backend.util

import java.time.LocalDate

object DateUtils {
    val EARLIEST_DATE: LocalDate = LocalDate.of(2025, 1, 1)

    fun parseAndValidateDate(date: String): LocalDate {
        val parsed =
            runCatching { LocalDate.parse(date) }
                .getOrElse { throw IllegalArgumentException("Invalid date format: $date") }
        require(parsed >= EARLIEST_DATE) { "Date is before the earliest available game" }
        require(parsed <= LocalDate.now()) { "Date is in the future" }
        return parsed
    }
}
