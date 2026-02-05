package com.choru.lock.domain

import com.choru.lock.infrastructure.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockService(
    private val repository: StockRepository
) {
    @Transactional
    fun decrease(id: Long, quantity: Long) {
        val stock = repository.findByIdWithOptimisticLock(id)
            ?: throw NoSuchElementException()
        stock.decrease(quantity)
        repository.saveAndFlush(stock)
    }
}
