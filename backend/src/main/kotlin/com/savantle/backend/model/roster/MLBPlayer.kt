package com.savantle.backend.model.roster

data class MLBPlayer(
    val mlbamId: Int,
    val fullName: String,
    val position: String,
    val throwingHand: String?,
    val team: MLBTeam,
    val onActiveRoster: Boolean = true,
    val rosterStatus: String? = null,
)
