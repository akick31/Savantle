package com.savantle.backend.util

import com.savantle.backend.model.DailyPlayer
import com.savantle.backend.model.MLBPlayer
import com.savantle.backend.model.PlayerSnapshot
import com.savantle.backend.model.RandomGame
import java.text.Normalizer

object PlayerUtils {
    val PITCHER_POSITIONS = setOf("SP", "RP", "P")

    fun normalizeForSearch(name: String): String {
        val nfd = Normalizer.normalize(name, Normalizer.Form.NFD)
        return nfd.replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase()
            .trim()
    }

    fun formatPosition(
        isPitcher: Boolean,
        throwingHand: String?,
        position: String,
    ): String {
        if (!isPitcher) return position
        return when (throwingHand?.uppercase()) {
            "L" -> "LHP"
            "R" -> "RHP"
            else -> "P"
        }
    }

    fun hint(
        type: String,
        label: String,
        value: String,
        confirmed: Boolean,
    ): Map<String, Any> = mapOf("type" to type, "label" to label, "value" to value, "confirmed" to confirmed)

    fun buildPlayerInfo(player: PlayerSnapshot): Map<String, String> =
        mapOf(
            "fullName" to player.fullName,
            "position" to formatPosition(player.isPitcher, player.throwingHand, player.position),
            "teamName" to player.teamName,
            "teamAbbr" to player.teamAbbr,
            "league" to player.league,
            "division" to player.division,
            "mlbamId" to player.mlbamId.toString(),
            "savantUrl" to player.savantUrl,
        )

    fun buildHints(
        player: PlayerSnapshot,
        guessNumber: Int,
        guessedPlayerName: String,
        rosterCache: List<MLBPlayer>,
    ): List<Map<String, Any>> {
        val hints = mutableListOf<Map<String, Any>>()
        val confirmedTypes = mutableSetOf<String>()

        val guessedPlayer =
            rosterCache.firstOrNull {
                normalizeForSearch(it.fullName) == normalizeForSearch(guessedPlayerName)
            }

        if (guessedPlayer != null) {
            when {
                guessedPlayer.team.name == player.teamName -> {
                    hints += hint("TEAM", "Team", player.teamName, confirmed = true)
                    hints += hint("DIVISION", "Division", player.division, confirmed = true)
                    hints += hint("LEAGUE", "League", player.league, confirmed = true)
                    confirmedTypes += setOf("TEAM", "DIVISION", "LEAGUE")
                }
                guessedPlayer.team.division == player.division -> {
                    hints += hint("DIVISION", "Division", player.division, confirmed = true)
                    hints += hint("LEAGUE", "League", player.league, confirmed = true)
                    confirmedTypes += setOf("DIVISION", "LEAGUE")
                }
            }
        }

        val scheduledType =
            when (guessNumber) {
                1 -> "POSITION"
                2 -> "LEAGUE"
                3 -> "DIVISION"
                4 -> "TEAM"
                else -> null
            }

        if (scheduledType != null && scheduledType !in confirmedTypes) {
            hints +=
                when (scheduledType) {
                    "POSITION" ->
                        hint(
                            "POSITION",
                            if (player.isPitcher) "Handedness" else "Position",
                            formatPosition(player.isPitcher, player.throwingHand, player.position),
                            confirmed = false,
                        )
                    "LEAGUE" -> hint("LEAGUE", "League", player.league, confirmed = false)
                    "DIVISION" -> hint("DIVISION", "Division", player.division, confirmed = false)
                    "TEAM" -> hint("TEAM", "Team", player.teamName, confirmed = false)
                    else -> return hints
                }
        }

        return hints
    }
}

fun DailyPlayer.toSnapshot() =
    PlayerSnapshot(
        fullName = fullName,
        mlbamId = mlbamId,
        position = position,
        throwingHand = throwingHand,
        isPitcher = isPitcher,
        teamName = teamName,
        teamAbbr = teamAbbr,
        league = league,
        division = division,
        savantUrl = savantUrl,
    )

fun RandomGame.toSnapshot() =
    PlayerSnapshot(
        fullName = fullName,
        mlbamId = mlbamId,
        position = position,
        throwingHand = throwingHand,
        isPitcher = isPitcher,
        teamName = teamName,
        teamAbbr = teamAbbr,
        league = league,
        division = division,
        savantUrl = savantUrl,
    )
