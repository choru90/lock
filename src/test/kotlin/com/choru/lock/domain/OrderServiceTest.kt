package com.choru.lock.domain

import com.choru.lock.OrderFacade
import com.choru.lock.infrastructure.CouponRepository
import com.choru.lock.infrastructure.OrderRepository
import com.choru.lock.infrastructure.StockRepository
import com.choru.lock.infrastructure.UserPointRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var orderFacade: OrderFacade

    @Autowired
    private lateinit var couponRepository: CouponRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    @Autowired
    private lateinit var pointRepository: UserPointRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    companion object {
        private const val THREAD_COUNT = 100
        private const val COUPON_ID = 1L
        private const val PRODUCT_ID = 1L
    }

    @BeforeEach
    fun setUp() {
        couponRepository.save(Coupon("선착순 할인 50%", 100))
        stockRepository.save(Stock(PRODUCT_ID, 100L))

        for (i in 1..THREAD_COUNT) {
            pointRepository.save(UserPoint(i.toLong(), 10000L))
        }
    }

    @AfterEach
    fun tearDown() {
        orderRepository.deleteAll()
        couponRepository.deleteAll()
        stockRepository.deleteAll()
        pointRepository.deleteAll()
    }

    @Test
    @DisplayName("전략 1: [All Optimistic] - 쿠폰/재고 충돌로 인해 재시도가 엄청나게 발생하지만 결국 성공한다")
    @Throws(InterruptedException::class)
    fun strategy1_optimistic() {
        runTest("Optimistic Lock") { userId ->
            try {
                orderFacade.order(userId, PRODUCT_ID, COUPON_ID)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
    }

    @Test
    @DisplayName("전략 2: [All Pessimistic] - 락 대기 시간은 있지만 재시도 없이 안정적으로 성공한다")
    @Throws(InterruptedException::class)
    fun strategy2_pessimistic() {
        runTest("Pessimistic Lock") { userId ->
            orderService.orderWithPessimisticLock(userId, PRODUCT_ID, COUPON_ID)
        }
    }

    @Test
    @DisplayName("전략 3: [Hybrid] - 쿠폰/재고는 줄 서고, 포인트는 바로 차감한다 (Best Practice)")
    @Throws(InterruptedException::class)
    fun strategy3_hybrid() {
        runTest("Hybrid Lock") { userId ->
            orderService.orderWithHybridLock(userId, PRODUCT_ID, COUPON_ID)
        }
    }

    @Test
    @DisplayName("증명: 비관적 락은 주문 중에 '포인트 충전'도 막아버린다 vs 하이브리드는 허용한다")
    @Throws(InterruptedException::class)
    fun prove_blocking() {
        val userId = 1L

        // 1. 전략 2 (All Pessimistic) 실행
        println("\n=== [전략 2: 비관적 락] 테스트 ===")
        runBlockingTest(
            userId,
            { orderService.orderWithPessimisticLock_Slow(userId, 1L, 1L) },
            "포인트 충전"
        )

        tearDown()
        setUp() // 데이터 초기화

        // 2. 전략 3 (Hybrid) 실행
        println("\n=== [전략 3: 하이브리드] 테스트 ===")
        runBlockingTest(
            userId,
            { orderService.orderWithHybridLock_Slow(userId, 1L, 1L) },
            "포인트 충전"
        )
    }

    @Throws(InterruptedException::class)
    private fun runBlockingTest(userId: Long, orderTask: Runnable, bTaskName: String) {
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)

        // 트랜잭션 템플릿 생성
        val txTemplate = TransactionTemplate(transactionManager)
        txTemplate.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW

        // [Thread A]
        executor.submit {
            try {
                println("A: 주문 시작 (Lock 획득 & 2초 대기)")
                orderTask.run()
                println("A: 주문 완료 (Lock 해제)")
            } catch (e: Exception) {
                System.err.println("A 에러: ${e.message}")
            } finally {
                latch.countDown()
            }
        }

        Thread.sleep(500) // 0.5초 대기

        // [Thread B]
        executor.submit {
            val start = System.currentTimeMillis()
            try {
                println("B: $bTaskName 시도...")

                txTemplate.execute {
                    val point = pointRepository.findByUserIdWithPessimisticLock(userId)
                        ?: throw NoSuchElementException()
                    point.use(0L)
                    null
                }

                val end = System.currentTimeMillis()
                println("B: $bTaskName 성공! (소요시간: ${end - start}ms)")
            } catch (e: Exception) {
                System.err.println("B 에러: ${e.message}")
            } finally {
                latch.countDown()
            }
        }

        latch.await()
    }

    @Throws(InterruptedException::class)
    private fun runTest(testName: String, task: (Long) -> Unit) {
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(THREAD_COUNT)

        val startTime = System.currentTimeMillis()
        println("[$testName] 테스트 시작...")

        for (i in 1..THREAD_COUNT) {
            val userId = i.toLong()
            executorService.submit {
                try {
                    task(userId)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        val endTime = System.currentTimeMillis()

        println("[$testName] 소요 시간: ${endTime - startTime}ms")

        // 검증
        val coupon = couponRepository.findById(COUPON_ID).orElseThrow()
        val stock = stockRepository.findById(PRODUCT_ID).orElseThrow()
        val orderCount = orderRepository.count()

        assertEquals(0, coupon.stock, "쿠폰 잔여량은 0이어야 함")
        assertEquals(0, stock.quantity, "재고 잔여량은 0이어야 함")
        assertEquals(100, orderCount, "주문은 100건 생성되어야 함")
    }
}
