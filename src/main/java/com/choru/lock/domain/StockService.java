package com.choru.lock.domain;

import com.choru.lock.infrastructure.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {
    private final StockRepository repository;


    @Transactional
    public void decrease(Long id, Long quantity){
        Stock stock = repository.findByIdWithOptimisticLock(id).orElseThrow();
        stock.decrease(quantity);
        repository.saveAndFlush(stock);

    }
}
