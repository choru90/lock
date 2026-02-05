package com.choru.lock.domain

import com.choru.lock.infrastructure.CouponRepository
import com.choru.lock.infrastructure.OrderRepository
import com.choru.lock.infrastructure.StockRepository
import com.choru.lock.infrastructure.UserPointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val stockRepository: StockRepository,
    private val couponRepository: CouponRepository,
    private val userPointRepository: UserPointRepository,
    private val orderRepository: OrderRepository
) {

    @Transactional
    fun orderWithOptimisticLock(userId: Long, productId: Long, couponId: Long) {
        val coupon = couponRepository.findById(couponId)
            .orElseThrow { IllegalArgumentException("쿠폰 없음") }
        coupon.decrease()

        val stock = stockRepository.findById(productId)
            .orElseThrow { IllegalArgumentException("재고 없음") }
        stock.decrease(1L)

        val point = userPointRepository.findByUserId(userId)
            ?: throw IllegalArgumentException("포인트 없음")
        point.use(1000L)

        orderRepository.save(Order(userId, productId, couponId))
    }

    @Transactional
    fun orderWithPessimisticLock(userId: Long, productId: Long, couponId: Long) {
        val coupon = couponRepository.findByWithPessimisticLock(couponId)
            ?: throw IllegalArgumentException("쿠폰 없음")
        coupon.decrease()

        val stock = stockRepository.findByIdWithPessimisticLock(productId)
            ?: throw IllegalArgumentException("재고 없음")
        stock.decrease(1L)

        val point = userPointRepository.findByUserIdWithPessimisticLock(userId)
            ?: throw IllegalArgumentException("포인트 없음")
        point.use(1000L)

        orderRepository.save(Order(userId, productId, couponId))
    }

    @Transactional
    fun orderWithHybridLock(userId: Long, productId: Long, couponId: Long) {
        val coupon = couponRepository.findByWithPessimisticLock(couponId)
            ?: throw IllegalArgumentException("쿠폰 없음")
        coupon.decrease()

        val stock = stockRepository.findByIdWithPessimisticLock(productId)
            ?: throw IllegalArgumentException("재고 없음")
        stock.decrease(1L)

        val point = userPointRepository.findByUserId(userId)
            ?: throw IllegalArgumentException("포인트 없음")
        point.use(1000L)

        orderRepository.save(Order(userId, productId, couponId))
    }

    @Transactional
    fun orderWithPessimisticLock_Slow(userId: Long, productId: Long, couponId: Long) {
        // 포인트 비관적 락 획득 (여기서부터 문 잠김 🔒)
        val point = userPointRepository.findByUserIdWithPessimisticLock(userId)
            ?: throw NoSuchElementException()

        // 외부 PG사 결제 승인 대기 (2초 소요)
        try {
            Thread.sleep(2000)
        } catch (_: InterruptedException) {
        }

        point.use(1000L)
        orderRepository.save(Order(userId, productId, couponId))
    }

    // [전략 3 기반] 느린 주문 (하이브리드)
    @Transactional
    fun orderWithHybridLock_Slow(userId: Long, productId: Long, couponId: Long) {
        // 포인트 낙관적 락 (조회만 함, 락 없음 🔓)
        val point = userPointRepository.findByUserId(userId)
            ?: throw NoSuchElementException()

        // 외부 PG사 결제 승인 대기 (2초 소요)
        try {
            Thread.sleep(2000)
        } catch (_: InterruptedException) {
        }

        point.use(1000L) // 커밋 시점에 버전 체크
        orderRepository.save(Order(userId, productId, couponId))
    }
}
