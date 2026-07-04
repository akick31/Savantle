package com.savantle.backend.model.dto.payload

data class RosterSnapshotPayload(
    val players: List<SnapshotPlayer>,
    val batterStats: List<SnapshotBatterStat>,
    val pitcherStats: List<SnapshotPitcherStat>,
    val seasonStartDate: String? = null,
    val rosterFetchedAt: String? = null,
    val statsFetchedAt: String? = null,
)
