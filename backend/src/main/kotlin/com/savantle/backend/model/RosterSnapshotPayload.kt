package com.savantle.backend.model

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

data class SnapshotBatterStat(
    val mlbamId: Int,
    val plateAppearances: Int,
)

data class SnapshotPitcherStat(
    val mlbamId: Int,
    val inningsPitched: Double,
    val gamesStarted: Int,
)

data class RosterSnapshotPayload(
    val players: List<SnapshotPlayer>,
    val batterStats: List<SnapshotBatterStat>,
    val pitcherStats: List<SnapshotPitcherStat>,
    val seasonStartDate: String? = null,
    val rosterFetchedAt: String? = null,
    val statsFetchedAt: String? = null,
)
