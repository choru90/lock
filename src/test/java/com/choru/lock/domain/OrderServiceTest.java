package com.choru.lock.domain;

import com.choru.lock.OrderFacade;
import com.choru.lock.infrastructure.CouponRepository;
import com.choru.lock.infrastructure.OrderRepository;
import com.choru.lock.infrastructure.StockRepository;
import com.choru.lock.infrastructure.UserPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderServiceTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderFacade orderFacade;

    @Autowired private CouponRepository couponRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private UserPointRepository pointRepository;
    @Autowired private OrderRepository orderRepository;

    @Autowired private PlatformTransactionManager transactionManager; // [필수 추가]

    private static final int THREAD_COUNT = 100;
    private static final Long COUPON_ID = 1L;
    private static final Long PRODUCT_ID = 1L;

    @BeforeEach
    void setUp(){
        couponRepository.save(new Coupon("선착순 할인 50%", 100));
        stockRepository.save(new Stock(PRODUCT_ID, 100L));

        for (long i = 1; i <=THREAD_COUNT; i++) {
            pointRepository.save(new UserPoint(i, 10000L));
        }
    }

    @AfterEach
    void tearDown(){
        orderRepository.deleteAll();
        couponRepository.deleteAll();
        stockRepository.deleteAll();
        pointRepository.deleteAll();
    }

    @Test
    @DisplayName("전략 1: [All Optimistic] - 쿠폰/재고 충돌로 인해 재시도가 엄청나게 발생하지만 결국 성공한다")
    void strategy1_optimistic() throws InterruptedException {
        runTest("Optimistic Lock", (userId) -> {
            try {
                // Facade를 통해 재시도 로직 수행
                orderFacade.order(userId, PRODUCT_ID, COUPON_ID);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("전략 2: [All Pessimistic] - 락 대기 시간은 있지만 재시도 없이 안정적으로 성공한다")
    void strategy2_pessimistic() throws InterruptedException {
        runTest("Pessimistic Lock", (userId) -> {
            // Service 직접 호출 (줄 서서 기다림)
            orderService.orderWithPessimisticLock(userId, PRODUCT_ID, COUPON_ID);
        });
    }

    @Test
    @DisplayName("전략 3: [Hybrid] - 쿠폰/재고는 줄 서고, 포인트는 바로 차감한다 (Best Practice)")
    void strategy3_hybrid() throws InterruptedException {
        runTest("Hybrid Lock", (userId) -> {
            // Service 직접 호출
            orderService.orderWithHybridLock(userId, PRODUCT_ID, COUPON_ID);
        });
    }

    @Test
    @DisplayName("증명: 비관적 락은 주문 중에 '포인트 충전'도 막아버린다 vs 하이브리드는 허용한다")
    void prove_blocking() throws InterruptedException {
        long userId = 1L;

        // 1. 전략 2 (All Pessimistic) 실행
        System.out.println("\n=== [전략 2: 비관적 락] 테스트 ===");
        runBlockingTest(userId,
                () -> orderService.orderWithPessimisticLock_Slow(userId, 1L, 1L), // A: 느린 주문
                "포인트 충전" // B: 충전 시도
        );

        tearDown(); setUp(); // 데이터 초기화

        // 2. 전략 3 (Hybrid) 실행
        System.out.println("\n=== [전략 3: 하이브리드] 테스트 ===");
        runBlockingTest(userId,
                () -> orderService.orderWithHybridLock_Slow(userId, 1L, 1L), // A: 느린 주문
                "포인트 충전" // B: 충전 시도
        );
    }

    private void runBlockingTest(long userId, Runnable orderTask, String bTaskName) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        // 트랜잭션 템플릿 생성 (프로그래밍 방식 트랜잭션 제어)
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 설정을 전파 속성으로 맞춤 (선택사항이나 기본값으로 충분)
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // [Thread A] - 기존과 동일 (Service 호출이라 알아서 트랜잭션 걸림)
        executor.submit(() -> {
            try {
                System.out.println("A: 주문 시작 (Lock 획득 & 2초 대기)");
                orderTask.run();
                System.out.println("A: 주문 완료 (Lock 해제)");
            } catch (Exception e) {
                System.err.println("A 에러: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        Thread.sleep(500); // 0.5초 대기

        // [Thread B] - [수정] 트랜잭션 템플릿으로 감싸기!
        executor.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                System.out.println("B: " + bTaskName + " 시도...");

                // [핵심] 트랜잭션 시작 -> 락 획득 -> 종료(커밋/락해제)
                txTemplate.execute(status -> {
                    // 이제 트랜잭션 안이므로 락 획득 가능!
                    UserPoint point = pointRepository.findByUserIdWithPessimisticLock(userId).orElseThrow();
                    point.use(0L);
                    return null;
                });

                long end = System.currentTimeMillis();
                System.out.println("B: " + bTaskName + " 성공! (소요시간: " + (end - start) + "ms)");
            } catch (Exception e) {
                System.err.println("B 에러: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        latch.await();
    }

    // 테스트 실행을 위한 공통 메소드 (템플릿)
    private void runTest(String testName, TaskConsumer task) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        // 시간 측정 시작
        long startTime = System.currentTimeMillis();
        System.out.println("[" + testName + "] 테스트 시작...");

        for (int i = 1; i <= THREAD_COUNT; i++) {
            final long userId = i; // 유저 ID 1~100
            executorService.submit(() -> {
                try {
                    task.accept(userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();

        System.out.println("[" + testName + "] 소요 시간: " + (endTime - startTime) + "ms");

        // 검증
        Coupon coupon = couponRepository.findById(COUPON_ID).orElseThrow();
        Stock stock = stockRepository.findById(PRODUCT_ID).orElseThrow();
        long orderCount = orderRepository.count();

        // 100개 다 팔렸는지 확인
        assertEquals(0, coupon.getStock(), "쿠폰 잔여량은 0이어야 함");
        assertEquals(0, stock.getQuantity(), "재고 잔여량은 0이어야 함");
        assertEquals(100, orderCount, "주문은 100건 생성되어야 함");
    }



    // 람다식 사용을 위한 함수형 인터페이스
    @FunctionalInterface
    interface TaskConsumer {
        void accept(Long userId);
    }

}