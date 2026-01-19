package com.choru.lock;

import com.choru.lock.domain.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;

    public void order(Long userId, Long productId, Long couponId) throws InterruptedException{
        while(true){
            try{
                orderService.orderWithOptimisticLock(userId, productId,couponId);
                break;
            }catch (ObjectOptimisticLockingFailureException e){
                // 버전 충돌시
                Thread.sleep(50);
            } catch (Exception e){
                // 재고 부족, 포인트 부족등 비즈니스 예외는 재시도 없이 종료
                throw e;
            }
        }
    }
}
