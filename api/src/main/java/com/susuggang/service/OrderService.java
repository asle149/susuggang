package com.susuggang.service;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Stock;
import com.susuggang.kafka.OrderCreatedEvent;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 비관적 락
    @Transactional
    public Long orderWithLock(Long buyerId, Long productId) {
        Stock stock = stockRepository.findByProductIdForUpdate(productId).orElseThrow();
        stock.decrease();
        return saveOrder(buyerId, productId);
    }

    // 조건부 UPDATE (확정 전략)
    @Transactional
    public Long orderWithConditionalUpdate(Long buyerId, Long productId) {
        if (stockRepository.decreaseStock(productId) == 0) {
            throw new IllegalStateException("재고 부족");
        }
        Long orderId = saveOrder(buyerId, productId);
        // 여기서 카프카로 바로 안 나감 — 커밋 성공 후 AFTER_COMMIT 리스너가 발행 (유령 이벤트 방지)
        eventPublisher.publishEvent(new OrderCreatedEvent(orderId, productId));
        return orderId;
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
