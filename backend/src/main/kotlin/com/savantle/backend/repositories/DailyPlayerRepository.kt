package com.savantle.backend.repositories

import com.savantle.backend.model.DailyPlayer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DailyPlayerRepository : JpaRepository<DailyPlayer, Long> {
    fun findByGameDate(gameDate: LocalDate): DailyPlayer?
    fun existsByGameDate(gameDate: LocalDate): Boolean
    fun findByGameDateBetween(start: LocalDate, end: LocalDate): List<DailyPlayer>

    @Modifying
    @Query("DELETE FROM DailyPlayer d WHERE d.gameDate = :gameDate")
    fun deleteByGameDate(gameDate: LocalDate): Int
}
