package com.savantle.backend.model.dto.payload

data class SnapshotPitcherStat(
    val mlbamId: Int,
    val inningsPitched: Double,
    val gamesStarted: Int,
)
