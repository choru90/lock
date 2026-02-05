package com.choru.lock.domain

import org.springframework.stereotype.Component

@Component
class StockFacade(
    private val service: StockService
) {
    @Throws(InterruptedException::class)
    fun decrease(id: Long, quantity: Long) {
        while (true) {
            try {
                service.decrease(id, quantity)
                break
            } catch (e: Exception) {
                Thread.sleep(50)
            }
        }
    }
}
