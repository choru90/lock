package com.choru.lock.domain;

import com.choru.lock.infrastructure.CouponRepository;
import com.choru.lock.infrastructure.OrderRepository;
import com.choru.lock.infrastructure.StockRepository;
import com.choru.lock.infrastructure.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final StockRepository stockRepository;
    private final CouponRepository couponRepository;
    private final UserPointRepository userPointRepository;
    private final OrderRepository orderRepository;


    @Transactional
    public void orderWithOptimisticLock(Long userId, Long productId, Long couponId){
        Coupon coupon  = couponRepository.findById(couponId).orElseThrow(() -> new IllegalArgumentException("쿠폰 없음"));
        coupon.decrease();

        Stock stock = stockRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("재고 없음"));
        stock.decrease(1L);

        UserPoint point = userPointRepository.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("포인트 없음"));

        point.use(1000L);

        orderRepository.save(new Order(userId, productId, couponId));
    }

    @Transactional
    public void orderWithPessimisticLock(Long userId, Long productId, Long couponId){
        Coupon coupon = couponRepository.findByWithPessimisticLock(couponId).orElseThrow(()-> new IllegalArgumentException("쿠폰 없음"));
        coupon.decrease();

        Stock stock = stockRepository.findByIdWithPessimisticLock(productId).orElseThrow(() -> new IllegalArgumentException("재고 없음"));
        stock.decrease(1L);

        UserPoint point = userPointRepository.findByUserIdWithPessimisticLock(userId).orElseThrow(()-> new IllegalArgumentException("포인트 없음"));
        point.use(1000L);

        orderRepository.save(new Order(userId, productId, couponId));
    }


    @Transactional
    public void orderWithHybridLock(Long userId, Long productId, Long couponId){
        Coupon coupon = couponRepository.findByWithPessimisticLock(couponId).orElseThrow(() -> new IllegalArgumentException("쿠폰 없음"));
        coupon.decrease();

        Stock stock = stockRepository.findByIdWithPessimisticLock(productId).orElseThrow(() -> new IllegalArgumentException("재고 없음"));
        stock.decrease(1L);

        UserPoint point = userPointRepository.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("포인트 없음"));
        point.use(1000L);
        orderRepository.save(new Order(userId, productId, couponId));

    }


    @Transactional
    public void orderWithPessimisticLock_Slow(Long userId, Long productId, Long couponId) {
        // ... (쿠폰, 재고 락 획득 생략) ...

        // 포인트 비관적 락 획득 (여기서부터 문 잠김 🔒)
        UserPoint point = userPointRepository.findByUserIdWithPessimisticLock(userId).orElseThrow();

        // 외부 PG사 결제 승인 대기 (2초 소요)
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        point.use(1000L);
        orderRepository.save(new Order(userId, productId, couponId));
    }

    // [전략 3 기반] 느린 주문 (하이브리드)
    @Transactional
    public void orderWithHybridLock_Slow(Long userId, Long productId, Long couponId) {
        // ... (쿠폰, 재고 락 획득 생략) ...

        // 포인트 낙관적 락 (조회만 함, 락 없음 🔓)
        UserPoint point = userPointRepository.findByUserId(userId).orElseThrow();

        // 외부 PG사 결제 승인 대기 (2초 소요)
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        point.use(1000L); // 커밋 시점에 버전 체크
        orderRepository.save(new Order(userId, productId, couponId));
    }
}
