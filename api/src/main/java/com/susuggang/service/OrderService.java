package com.susuggang.service;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Stock;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;

    // 비관적 락
    @Transactional
    public Long orderWithLock(Long buyerId, Long productId) {
        Stock stock = stockRepository.findByProductIdForUpdate(productId).orElseThrow();
        stock.decrease();
        return saveOrder(buyerId, productId);
    }

    // 조건부 UPDATE
    @Transactional
    public Long orderWithConditionalUpdate(Long buyerId, Long productId) {
        if (stockRepository.decreaseStock(productId) == 0) {
            throw new IllegalStateException("재고 부족");
        }
        return saveOrder(buyerId, productId);
    }

    // 낙관적 락
    @Transactional
    public Long orderOptimisticOnce(Long buyerId, Long productId){
        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        stock.decrease();
        return saveOrder(buyerId, productId);
    }

    // 공통: 주문 저장
    private Long saveOrder(Long buyerId, Long productId){
        Order order = orderRepository.save(Order.builder()
                .buyerId(buyerId).productId(productId)
                .status(OrderStatus.COMPLETED).build());
        return order.getId();
    }
}
