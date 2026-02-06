package com.choru.lock.domain

import com.choru.lock.infrastructure.StockRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.TimeUnit

@Service
class StockService(
    private val repository: StockRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {
        // LOCK
//    @Transactional
//    fun decrease(id: Long, quantity: Long) {
//        val stock = repository.findByIdWithOptimisticLock(id)
//            ?: throw NoSuchElementException()
//        stock.decrease(quantity)
//        repository.saveAndFlush(stock)
//    }


    // Lock 없음
//    @Transactional
//    fun decrease(id: Long, quantity: Long) {
//        val stock = repository.findById(id).orElseThrow {
//            throw IllegalArgumentException("재고가 없습니다.")
//        }
//        stock.decrease(quantity)
//    }


    /**
     * [Read] 재고 조회 (Cache Look-aside)
     * 1. Redis에 있으면 반환 (Fast)
     * 2. 없으면 DB 조회 -> Redis 저장 -> 반환 (Slow -> Fast)
     */
    fun getStockQuantity(id: Long): Long {
        val cacheKey = "stock:$id"

        // 1.redis 조회
        val cacheQty = redisTemplate.opsForValue().get(cacheKey)
        if(cacheQty != null){
            return cacheQty.toString().toLong()
        }

        // 2. DB 조회
        val stock = repository.findById(id).orElseThrow{
            IllegalArgumentException("재고가 없습니다.")
        }

        // 3.  Redis 저장(TTL 1분: 재고는 변동이 심하므로 짧게 가져감)
        redisTemplate.opsForValue().set(cacheKey, stock.quantity, 60, TimeUnit.SECONDS)

        return stock.quantity
    }


    /**
     * [Write] 재고 감소 (Cache Eviction)
     * DB 업데이트 후 캐시를 '삭제'하여 다음 조회 시 DB에서 새 값을 가져오게 강제함.
     * (이 방식이 데이터 불일치를 막는 가장 확실한 방법입니다)
     */
    @Transactional
    fun decrease(id: Long, quantity: Long) {
        // 1. DB 업데이트
        val stock = repository.findById(id).orElseThrow {
            IllegalArgumentException("재고가 없습니다.")
        }
        stock.decrease(quantity)

        // 2. Redis 캐시 삭제 (Eviction)
        // 트랜젝션 커밋 직전에 삭제하거나, 이렇게 메소드 마지막에 삭제
//        redisTemplate.delete("stock:$id")

        // 2. 커밋 후 실행할 작업 예약 (Non-Blocking)
        // 현재 트랜잭션이 성공적으로 커밋된 "직후"에 실행됨
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization{
            override fun afterCommit() {
                // DB 락이 다 풀린 상태에서 안전하게 Redis 삭제
                redisTemplate.delete("stock:$id")
            }
        })
    }
}
