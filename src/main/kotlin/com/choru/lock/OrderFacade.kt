package com.choru.lock

import com.choru.lock.domain.OrderService
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderService: OrderService
) {
    @Throws(InterruptedException::class)
    fun order(userId: Long, productId: Long, couponId: Long) {
        while (true) {
            try {
                orderService.orderWithOptimisticLock(userId, productId, couponId)
                break
            } catch (e: ObjectOptimisticLockingFailureException) {
                // 버전 충돌시 재시도
                Thread.sleep(50)
            } catch (e: Exception) {
                // 재고 부족, 포인트 부족등 비즈니스 예외는 재시도 없이 종료
                throw e
            }
        }
    }
}
