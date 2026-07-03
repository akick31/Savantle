package com.savantle.backend.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MLBRosterServiceTest {
    private val service = MLBRosterService()

    @Test
    fun `parses savant csv with bom quoted commas and empty fields`() {
        val csv =
            "﻿\"last_name, first_name\",\"player_id\",\"year\",\"p_formatted_ip\",\"p_starting_p\"\n" +
                "\"Taillon, Jameson\",592791,2026,\"67.2\",\"13\"\n" +
                "\"O'Neill, Tyler \"\"TO\"\"\",641933,2026,\"6.1\",\n"
        val rows = service.parseSavantCsv(csv)

        assertEquals(2, rows.size)
        assertEquals("Taillon, Jameson", rows[0]["last_name, first_name"])
        assertEquals("592791", rows[0]["player_id"])
        assertEquals("67.2", rows[0]["p_formatted_ip"])
        assertEquals("13", rows[0]["p_starting_p"])
        assertEquals("O'Neill, Tyler \"TO\"", rows[1]["last_name, first_name"])
        assertEquals("", rows[1]["p_starting_p"])
    }

    @Test
    fun `parses empty csv without rows`() {
        assertTrue(service.parseSavantCsv("").isEmpty())
        assertTrue(service.parseSavantCsv("\"player_id\",\"pa\"").isEmpty())
    }
}
