package com.savantle.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "daily_player",
    indexes = [Index(name = "idx_daily_player_game_date", columnList = "game_date", unique = true)]
)
class DailyPlayer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "game_date", nullable = false, unique = true)
    var gameDate: LocalDate,

    @Column(name = "mlbam_id", nullable = false)
    var mlbamId: Int,

    @Column(name = "full_name", nullable = false, length = 200)
    var fullName: String,

    @Column(name = "normalized_name", nullable = false, length = 200)
    var normalizedName: String,

    @Column(name = "position", nullable = false, length = 16)
    var position: String,

    @Column(name = "is_pitcher", nullable = false)
    var isPitcher: Boolean,

    @Column(name = "team_name", nullable = false, length = 100)
    var teamName: String,

    @Column(name = "team_abbr", nullable = false, length = 10)
    var teamAbbr: String,

    @Column(name = "league", nullable = false, length = 8)
    var league: String,

    @Column(name = "division", nullable = false, length = 32)
    var division: String,

    @Column(name = "savant_url", nullable = false, length = 500)
    var savantUrl: String,

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "screenshot", nullable = false, columnDefinition = "LONGBLOB")
    var screenshot: ByteArray,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
