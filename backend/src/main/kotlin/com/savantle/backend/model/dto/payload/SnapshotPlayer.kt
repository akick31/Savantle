package com.savantle.backend.model.dto.payload

data class SnapshotPlayer(
    val mlbamId: Int,
    val fullName: String,
    val position: String,
    val throwingHand: String? = null,
    val teamId: Int,
    val teamName: String,
    val teamAbbr: String,
    val league: String,
    val division: String,
    val onActiveRoster: Boolean = true,
    val rosterStatus: String? = null,
)
