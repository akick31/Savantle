package com.savantle.backend.model

data class MLBPlayer(
    val mlbamId: Int,
    val fullName: String,
    val position: String,
    val throwingHand: String?,
    val team: MLBTeam,
)
