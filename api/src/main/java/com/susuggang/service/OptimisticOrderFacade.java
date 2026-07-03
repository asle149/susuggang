package com.susuggang.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class OptimisticOrderFacade {

    private final OrderService orderService;
    private final AtomicInteger retryCount = new AtomicInteger();

    public Long order(Long buyerId, Long productId){
        for(int i=0; i<100; i++){
            try {
                return orderService.orderOptimisticOnce(buyerId, productId);
            }catch (OptimisticLockingFailureException e){
                retryCount.incrementAndGet();
            }
        }
        throw new IllegalStateException("재고 부족");
    }

    public int getRetryCount(){
        return retryCount.get();
    }
}
