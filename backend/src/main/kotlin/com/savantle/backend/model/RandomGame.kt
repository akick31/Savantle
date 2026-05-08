package com.savantle.backend.model

import java.time.Instant

data class RandomGame(
    val gameId: String,
    val mlbamId: Int,
    val fullName: String,
    val normalizedName: String,
    val position: String,
    val throwingHand: String?,
    val isPitcher: Boolean,
    val teamName: String,
    val teamAbbr: String,
    val league: String,
    val division: String,
    val savantUrl: String,
    val screenshot: ByteArray,
    val createdAt: Instant = Instant.now(),
)
