package com.savantle.backend.model

import java.time.Instant
import java.time.LocalDate

data class PitcherLine(
    val inningsPitched: Double,
    val gamesStarted: Int,
)

data class RosterData(
    val players: List<MLBPlayer>,
    val batterPa: Map<Int, Int>,
    val pitcherLines: Map<Int, PitcherLine>,
    val seasonStartDate: LocalDate?,
    val rosterFetchedAt: Instant,
    val statsFetchedAt: Instant,
)
