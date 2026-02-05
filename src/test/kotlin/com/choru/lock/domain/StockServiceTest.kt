package com.choru.lock.domain

import com.choru.lock.infrastructure.StockRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SpringBootTest
class StockServiceTest {

    @Autowired
    private lateinit var service: StockService

    @Autowired
    private lateinit var repository: StockRepository

    @Autowired
    private lateinit var facade: StockFacade

    @Autowired
    private lateinit var redissonLockStockFacade: RedissonLockStockFacade

    @BeforeEach
    fun before() {
        repository.saveAndFlush(Stock(1L, 100L))
    }

    @AfterEach
    fun delete() {
        repository.deleteAll()
    }
    // Lock 일떄
//    @Test
//    @DisplayName("동시에 100명이 주문하면 재고가 0이 되어야 한다")
//    @Throws(InterruptedException::class)
//    fun concurrentRequest() {
//        val threadCount = 100
//        val executorService = Executors.newFixedThreadPool(32)
//        val latch = CountDownLatch(threadCount)
//
//        for (i in 0 until threadCount) {
//            executorService.submit {
//                try {
//                    facade.decrease(1L, 1L)
//                } catch (e: InterruptedException) {
//                    throw RuntimeException(e)
//                } finally {
//                    latch.countDown()
//                }
//            }
//        }
//        latch.await()
//        val stock = repository.findById(1L).orElseThrow()
//        assertEquals(0, stock.quantity)
//    }

    @Test
    fun `분산락_100명_동시요청_테스트`() {
        val threadCount = 100
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        val startTime = System.currentTimeMillis()

        for (i in 0 until threadCount) {
            executorService.submit {
                try {
                    // Redisson 분산 락 사용
                    redissonLockStockFacade.decrease(1L, 1L)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        val endTime = System.currentTimeMillis()

        val stock = repository.findAll()[0]

        println("=== [Redisson 분산 락] 소요 시간: ${endTime - startTime}ms ===")
        assertEquals(0L, stock.quantity)
    }
}
