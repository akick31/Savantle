package com.savantle.backend.model

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
)
