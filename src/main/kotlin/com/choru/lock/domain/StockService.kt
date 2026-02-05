package com.choru.lock.domain

import com.choru.lock.infrastructure.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockService(
    private val repository: StockRepository
) {
        // LOCK
//    @Transactional
//    fun decrease(id: Long, quantity: Long) {
//        val stock = repository.findByIdWithOptimisticLock(id)
//            ?: throw NoSuchElementException()
//        stock.decrease(quantity)
//        repository.saveAndFlush(stock)
//    }

    @Transactional
    fun decrease(id: Long, quantity: Long) {
        val stock = repository.findById(id).orElseThrow {
            throw IllegalArgumentException("재고가 없습니다.")
        }
        stock.decrease(quantity)
    }
}
