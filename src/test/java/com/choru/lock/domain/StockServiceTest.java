package com.choru.lock.domain;

import com.choru.lock.infrastructure.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockServiceTest {

    @Autowired private StockService service;
    @Autowired private StockRepository repository;
    @Autowired private StockFacade facade;


    @BeforeEach
    public void before(){
        repository.saveAndFlush(new Stock(1L, 100L));
    }

    @Test
    @DisplayName("동시에 100명이 주문하면 재고가 0이 되어야 한다")
    public void concurrentRequest() throws InterruptedException{
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for(int i = 0; i< threadCount; i++){
            executorService.submit(()-> {
                try{
                    facade.decrease(1L, 1L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        Stock stock = repository.findById(1L).orElseThrow();
        assertEquals(0, stock.getQuantity());
    }

}