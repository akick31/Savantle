package com.savantle.backend.services

import com.savantle.backend.model.MLBPlayer
import com.savantle.backend.model.MLBTeam
import com.savantle.backend.model.PitcherLine
import com.savantle.backend.model.RosterData
import com.savantle.backend.model.RosterSnapshot
import com.savantle.backend.repositories.RosterSnapshotRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class RosterSnapshotServiceTest {
    private val repository = mock(RosterSnapshotRepository::class.java)
    private val service = RosterSnapshotService(repository)

    private val dodgers = MLBTeam(id = 119, name = "Dodgers", abbreviation = "LAD", league = "NL", division = "NL West")
    private val angels = MLBTeam(id = 108, name = "Angels", abbreviation = "LAA", league = "AL", division = "AL West")

    private val data =
        RosterData(
            players =
                listOf(
                    MLBPlayer(660271, "Shohei Ohtani", "SP", "R", dodgers),
                    MLBPlayer(660271, "Shohei Ohtani", "DH", "R", dodgers),
                    MLBPlayer(545361, "Mike Trout", "CF", "R", angels, onActiveRoster = false, rosterStatus = "Injured List 10-Day"),
                ),
            batterPa = mapOf(660271 to 320, 545361 to 12),
            pitcherLines = mapOf(660271 to PitcherLine(55.333, 10)),
            seasonStartDate = LocalDate.of(2026, 3, 25),
            rosterFetchedAt = Instant.parse("2026-07-02T09:00:00Z"),
            statsFetchedAt = Instant.parse("2026-07-01T09:00:00Z"),
        )

    @Test
    fun `save and load roundtrip the roster data`() {
        val captor = ArgumentCaptor.forClass(RosterSnapshot::class.java)
        `when`(repository.save(captor.capture())).thenAnswer { it.arguments[0] }
        service.save(data)

        `when`(repository.findById(RosterSnapshot.SINGLETON_ID)).thenReturn(Optional.of(captor.value))
        val loaded = service.load()!!

        assertEquals(data, loaded)
    }

    @Test
    fun `load splits players stored with a TWP position`() {
        val payload =
            """
            {"players":[{"mlbamId":660271,"fullName":"Shohei Ohtani","position":"TWP","throwingHand":"R",
            "teamId":119,"teamName":"Dodgers","teamAbbr":"LAD","league":"NL","division":"NL West"}],
            "batterStats":[],"pitcherStats":[],"seasonStartDate":null}
            """.trimIndent()
        `when`(repository.findById(RosterSnapshot.SINGLETON_ID))
            .thenReturn(Optional.of(RosterSnapshot(payload = payload, updatedAt = Instant.now())))

        val loaded = service.load()!!
        assertEquals(listOf("SP", "DH"), loaded.players.map { it.position })
        assertEquals("NL West", loaded.players.first().team.division)
    }

    @Test
    fun `load returns null when no snapshot exists`() {
        `when`(repository.findById(RosterSnapshot.SINGLETON_ID)).thenReturn(Optional.empty())
        assertNull(service.load())
    }
}
