package com.savantle.backend.repositories

import com.savantle.backend.model.RosterSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RosterSnapshotRepository : JpaRepository<RosterSnapshot, Long>
