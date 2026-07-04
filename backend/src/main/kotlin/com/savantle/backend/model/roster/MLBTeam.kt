package com.savantle.backend.model.roster

data class MLBTeam(
    val id: Int,
    val name: String,
    val abbreviation: String,
    val league: String,
    val division: String,
)
