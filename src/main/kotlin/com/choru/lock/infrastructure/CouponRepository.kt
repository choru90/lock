package com.choru.lock.infrastructure

import com.choru.lock.domain.Coupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CouponRepository : JpaRepository<Coupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    fun findByWithPessimisticLock(id: Long): Coupon?

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select c from Coupon c where c.id = :id")
    fun findByWithOptimisticLock(id: Long): Coupon?
}
