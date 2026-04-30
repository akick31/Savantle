package com.savantle.backend.repositories

import com.savantle.backend.model.DailyPlayer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DailyPlayerRepository : JpaRepository<DailyPlayer, Long> {
    fun findByGameDate(gameDate: LocalDate): DailyPlayer?
    fun existsByGameDate(gameDate: LocalDate): Boolean
    fun deleteByGameDate(gameDate: LocalDate): Long
}
