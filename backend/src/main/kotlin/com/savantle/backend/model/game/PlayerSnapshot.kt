package com.savantle.backend.model.game

import java.time.LocalDate

data class PlayerSnapshot(
    val fullName: String,
    val mlbamId: Int,
    val position: String,
    val throwingHand: String?,
    val isPitcher: Boolean,
    val teamName: String,
    val teamAbbr: String,
    val league: String,
    val division: String,
    val savantUrl: String,
    val inningsPitched: Double? = null,
    val gamesStarted: Int? = null,
    val lastGamePlayed: LocalDate? = null,
)
