package com.choru.lock.infrastructure

import com.choru.lock.domain.UserPoint
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserPointRepository : JpaRepository<UserPoint, Long> {

    fun findByUserId(userId: Long): UserPoint?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select up from UserPoint up where up.userId = :userId")
    fun findByUserIdWithPessimisticLock(userId: Long): UserPoint?
}
