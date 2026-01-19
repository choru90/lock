package com.choru.lock.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockFacade {
    private final StockService service;

    public void decrease(Long id, Long quantity) throws InterruptedException{
        while (true){
            try{
                service.decrease(id, quantity);

                break;
            }catch (Exception e){
                Thread.sleep(50);
            }
        }
    }

}
