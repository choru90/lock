package com.choru.lock.domain

import org.apache.commons.logging.LogFactory
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedissonLockStockFacade(
    private val redissonClient: RedissonClient,
    private val stockService: StockService
) {

    private val log = LogFactory.getLog(this::class.java)

    fun decrease(key: Long, quantity: Long){

        // 1. 락 이름 정의(고유해야 함)
        val lockName = "lock:stock:$key"
        val lock = redissonClient.getLock(lockName)

        try{
            // 2. 락 획득 시도 (대기 시간 10초, 점유 시간 1초)
            val available = lock.tryLock(10, 1, TimeUnit.SECONDS)
            if(!available){
                log.info("현재 락 획득 실패 (트래픽 폭주)")
            }
            // 3. 비즈니스 로직 수행
            // 주의: 여기서 호출하는 stockService.decrease는 @Lock이 없는 일반 메소드여야 합니다.
            stockService.decrease(key, quantity)

        }catch (e: InterruptedException){
            throw RuntimeException(e)
        }finally {
            if(lock.isLocked && lock.isHeldByCurrentThread){
                lock.unlock()
            }
        }
    }
}