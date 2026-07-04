package com.savantle.backend.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.savantle.backend.model.dto.payload.RosterSnapshotPayload
import com.savantle.backend.model.dto.payload.SnapshotBatterStat
import com.savantle.backend.model.dto.payload.SnapshotPitcherStat
import com.savantle.backend.model.dto.payload.SnapshotPlayer
import com.savantle.backend.model.entity.RosterSnapshot
import com.savantle.backend.model.roster.MLBPlayer
import com.savantle.backend.model.roster.MLBTeam
import com.savantle.backend.model.roster.PitcherLine
import com.savantle.backend.model.roster.RosterData
import com.savantle.backend.repositories.RosterSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

@Service
class RosterSnapshotService(
    private val rosterSnapshotRepository: RosterSnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(RosterSnapshotService::class.java)
    private val mapper = jacksonObjectMapper()

    fun save(data: RosterData): RosterData {
        val payload = fromRosterData(data)
        rosterSnapshotRepository.save(
            RosterSnapshot(
                id = RosterSnapshot.SINGLETON_ID,
                payload = mapper.writeValueAsString(payload),
                updatedAt = Instant.now(),
            ),
        )
        log.info(
            "Roster snapshot persisted: ${payload.players.size} players, " +
                "${payload.batterStats.size} batter stats, ${payload.pitcherStats.size} pitcher stats",
        )
        return data
    }

    fun load(): RosterData? {
        val snapshot = rosterSnapshotRepository.findById(RosterSnapshot.SINGLETON_ID).orElse(null) ?: return null
        return try {
            toRosterData(mapper.readValue<RosterSnapshotPayload>(snapshot.payload), snapshot.updatedAt)
        } catch (e: Exception) {
            log.error("Failed to parse persisted roster snapshot", e)
            null
        }
    }

    private fun toRosterData(
        payload: RosterSnapshotPayload,
        savedAt: Instant,
    ): RosterData {
        val players =
            payload.players.flatMap { p ->
                val team =
                    MLBTeam(
                        id = p.teamId,
                        name = p.teamName,
                        abbreviation = p.teamAbbr,
                        league = p.league,
                        division = p.division,
                    )
                val positions = if (p.position == "TWP") listOf("SP", "DH") else listOf(p.position)
                positions.map { pos ->
                    MLBPlayer(
                        mlbamId = p.mlbamId,
                        fullName = p.fullName,
                        position = pos,
                        throwingHand = p.throwingHand,
                        team = team,
                        onActiveRoster = p.onActiveRoster,
                        rosterStatus = p.rosterStatus,
                    )
                }
            }
        return RosterData(
            players = players,
            batterPa = payload.batterStats.associate { it.mlbamId to it.plateAppearances },
            pitcherLines = payload.pitcherStats.associate { it.mlbamId to PitcherLine(it.inningsPitched, it.gamesStarted) },
            seasonStartDate = payload.seasonStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            rosterFetchedAt = payload.rosterFetchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: savedAt,
            statsFetchedAt = payload.statsFetchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: savedAt,
        )
    }

    private fun fromRosterData(data: RosterData): RosterSnapshotPayload {
        return RosterSnapshotPayload(
            players =
                data.players.map { p ->
                    SnapshotPlayer(
                        mlbamId = p.mlbamId,
                        fullName = p.fullName,
                        position = p.position,
                        throwingHand = p.throwingHand,
                        teamId = p.team.id,
                        teamName = p.team.name,
                        teamAbbr = p.team.abbreviation,
                        league = p.team.league,
                        division = p.team.division,
                        onActiveRoster = p.onActiveRoster,
                        rosterStatus = p.rosterStatus,
                    )
                },
            batterStats = data.batterPa.map { (id, pa) -> SnapshotBatterStat(id, pa) },
            pitcherStats = data.pitcherLines.map { (id, line) -> SnapshotPitcherStat(id, line.inningsPitched, line.gamesStarted) },
            seasonStartDate = data.seasonStartDate?.toString(),
            rosterFetchedAt = data.rosterFetchedAt.toString(),
            statsFetchedAt = data.statsFetchedAt.toString(),
        )
    }
}
