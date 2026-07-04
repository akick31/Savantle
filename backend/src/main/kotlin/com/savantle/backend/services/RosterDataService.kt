package com.savantle.backend.services

import com.savantle.backend.model.MLBPlayer
import com.savantle.backend.model.PitcherLine
import com.savantle.backend.model.RosterData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Service
class RosterDataService(
    private val mlbRosterService: MLBRosterService,
    private val rosterSnapshotService: RosterSnapshotService,
    @Value("\${savantle.qualification.batter-min-pa:75}") private val minBatterPa: Int,
    @Value("\${savantle.qualification.pitcher-min-ip:15.0}") private val minPitcherIp: Double,
    @Value("\${savantle.qualification.starter-ip-target:120.0}") private val starterIpTarget: Double,
    @Value("\${savantle.qualification.reliever-ip-target:50.0}") private val relieverIpTarget: Double,
    @Value("\${savantle.qualification.early-season-days:30}") private val earlySeasonDays: Long,
    @Value("\${savantle.roster.max-staleness-days:14}") private val maxStalenessDays: Long,
) {
    companion object {
        private const val SEASON_LENGTH_DAYS = 183.0
        private const val MIN_EXPECTED_ROSTER_SIZE = 500
    }

    private val log = LoggerFactory.getLogger(RosterDataService::class.java)

    @Volatile private var rosterCache: List<MLBPlayer> = emptyList()

    @Volatile private var rosterCacheDate: LocalDate? = null

    @Volatile private var qualifiedIdsCache: Set<Int> = emptySet()

    @Volatile private var batterPa: Map<Int, Int> = emptyMap()

    @Volatile private var pitcherLines: Map<Int, PitcherLine> = emptyMap()

    @Volatile private var rosterFetchedAt: Instant? = null

    @Volatile private var statsFetchedAt: Instant? = null

    @Volatile private var seasonStartDate: LocalDate? = null

    fun players(): List<MLBPlayer> = rosterCache

    fun qualifiedIds(): Set<Int> = qualifiedIdsCache

    fun rosterTimestamp(): Instant? = rosterFetchedAt

    fun statsTimestamp(): Instant? = statsFetchedAt

    fun isRosterUsable(): Boolean = isFresh(rosterFetchedAt)

    fun areStatsUsable(): Boolean = isFresh(statsFetchedAt)

    fun refreshUntilReady() {
        var delaySeconds = 60L
        while (rosterCache.isEmpty()) {
            refresh()
            if (rosterCache.isNotEmpty()) return
            log.warn("Roster cache still empty after refresh attempt — retrying in ${delaySeconds}s")
            Thread.sleep(delaySeconds * 1000)
            delaySeconds = (delaySeconds * 2).coerceAtMost(1800)
        }
    }

    fun ensureFreshForToday() {
        if (rosterCache.isEmpty() || rosterCacheDate != LocalDate.now()) refresh()
    }

    fun refresh() {
        if (rosterCache.isEmpty()) {
            val snapshot =
                try {
                    rosterSnapshotService.load()
                } catch (e: Exception) {
                    log.error("Failed to load roster snapshot", e)
                    null
                }
            if (snapshot != null) {
                applyRosterData(snapshot)
                log.info("Loaded persisted snapshot (roster from ${snapshot.rosterFetchedAt}, stats from ${snapshot.statsFetchedAt})")
            }
        }

        val year = LocalDate.now().year
        val now = Instant.now()
        val freshRoster = fetchFreshRoster()

        if (seasonStartDate == null) {
            seasonStartDate = mlbRosterService.fetchSeasonStartDate(year)
            log.info("Season start date: $seasonStartDate")
        }

        val freshStats =
            try {
                mlbRosterService.fetchQualificationStatsWithFallback(year)
            } catch (e: Exception) {
                log.warn("All qualification stats sources failed: ${e.message}")
                null
            }

        if (freshRoster == null && freshStats == null) {
            qualifiedIdsCache = computeQualifiedIds()
            log.error("Refresh fetched nothing new — keeping roster from $rosterFetchedAt, stats from $statsFetchedAt")
            return
        }

        val merged =
            RosterData(
                players = freshRoster ?: rosterCache,
                batterPa = freshStats?.first ?: batterPa,
                pitcherLines = freshStats?.second ?: pitcherLines,
                seasonStartDate = seasonStartDate,
                rosterFetchedAt = if (freshRoster != null) now else rosterFetchedAt ?: Instant.EPOCH,
                statsFetchedAt = if (freshStats != null) now else statsFetchedAt ?: Instant.EPOCH,
            )
        applyRosterData(merged)
        try {
            rosterSnapshotService.save(merged)
        } catch (e: Exception) {
            log.error("Failed to persist roster snapshot", e)
        }
    }

    private fun fetchFreshRoster(): List<MLBPlayer>? {
        return try {
            val players = mlbRosterService.fetchActiveRosters()
            if (players.size >= MIN_EXPECTED_ROSTER_SIZE) {
                players
            } else {
                log.warn("Roster fetch returned only ${players.size} players (expected >= $MIN_EXPECTED_ROSTER_SIZE) — keeping previous roster")
                null
            }
        } catch (e: Exception) {
            log.warn("Roster fetch failed: ${e.message}")
            null
        }
    }

    private fun applyRosterData(data: RosterData) {
        if (data.players.isNotEmpty()) {
            rosterCache = data.players
            rosterCacheDate = LocalDate.now()
            rosterFetchedAt = data.rosterFetchedAt.takeIf { it != Instant.EPOCH }
        }
        batterPa = data.batterPa
        pitcherLines = data.pitcherLines
        statsFetchedAt = data.statsFetchedAt.takeIf { it != Instant.EPOCH }
        if (data.seasonStartDate != null) seasonStartDate = data.seasonStartDate
        qualifiedIdsCache = computeQualifiedIds()
        log.info(
            "Roster data applied: ${data.players.size} players (fetched ${data.rosterFetchedAt}), " +
                "${qualifiedIdsCache.size} qualified (stats fetched ${data.statsFetchedAt})",
        )
    }

    private fun computeQualifiedIds(): Set<Int> {
        val (starterFloor, relieverFloor) = computePitcherFloors()
        val ids = mutableSetOf<Int>()
        batterPa.forEach { (id, pa) -> if (pa >= minBatterPa) ids.add(id) }
        pitcherLines.forEach { (id, line) ->
            val floor = if (line.gamesStarted > 0) starterFloor else relieverFloor
            if (line.inningsPitched >= floor) ids.add(id)
        }
        return ids
    }

    private fun computePitcherFloors(): Pair<Double, Double> {
        val start = seasonStartDate ?: return Pair(minPitcherIp, minPitcherIp)
        val progress = ((LocalDate.now().toEpochDay() - start.toEpochDay()) / SEASON_LENGTH_DAYS).coerceIn(0.0, 1.0)
        return Pair(
            maxOf(minPitcherIp, progress * starterIpTarget),
            maxOf(minPitcherIp, progress * relieverIpTarget),
        )
    }

    private fun isFresh(timestamp: Instant?): Boolean {
        if (timestamp == null) return false
        return Duration.between(timestamp, Instant.now()).toDays() <= maxStalenessDays
    }

    fun pitcherLineFor(
        mlbamId: Int,
        year: Int,
    ): Pair<Double, Int>? =
        pitcherLines[mlbamId]?.let { it.inningsPitched to it.gamesStarted }
            ?: mlbRosterService.fetchPitcherStats(mlbamId, year)

    fun lastGamePlayedFor(
        mlbamId: Int,
        year: Int,
        isPitcher: Boolean,
    ): LocalDate? = mlbRosterService.fetchLastGamePlayed(mlbamId, year, isPitcher)

    fun isEarlySeason(date: LocalDate): Boolean {
        val start = seasonStartDate ?: return true
        return date < start.plusDays(earlySeasonDays)
    }

    fun isPlayerEligible(player: MLBPlayer): Boolean =
        player.onActiveRoster &&
            (isEarlySeason(LocalDate.now()) || qualifiedIdsCache.isEmpty() || player.mlbamId in qualifiedIdsCache)

    fun eligibilityReason(
        player: MLBPlayer,
        isPitcher: Boolean,
    ): String {
        if (!player.onActiveRoster) {
            return if (player.rosterStatus?.startsWith("Injured", ignoreCase = true) == true) {
                "Warning: ${player.fullName} is currently on the IL"
            } else {
                "Warning: ${player.fullName} is not on an active MLB roster"
            }
        }
        val threshold = if (isPitcher) "IP threshold for pitchers" else "PA threshold for batters"
        return "Warning: ${player.fullName} hasn't reached the $threshold"
    }

    fun buildRandomPool(excludedMlbamIds: Set<Int>): List<MLBPlayer> {
        val activeRoster = rosterCache.filter { it.onActiveRoster }
        val basePool =
            if (!isEarlySeason(LocalDate.now()) && qualifiedIdsCache.isNotEmpty()) {
                val filtered = activeRoster.filter { it.mlbamId in qualifiedIdsCache }
                filtered.ifEmpty { activeRoster }
            } else {
                activeRoster
            }
        return basePool.filter { it.mlbamId !in excludedMlbamIds }
    }
}
