package com.susuggang.service;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Stock;
import com.susuggang.kafka.OrderCreatedEvent;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${order.reservation-ttl}")
    private Duration reservationTtl;

    // 비관적 락
    @Transactional
    public Long orderWithLock(Long buyerId, Long productId) {
        Stock stock = stockRepository.findByProductIdForUpdate(productId).orElseThrow();
        stock.decrease();
        return saveOrder(buyerId, productId, OrderStatus.COMPLETED, null);
    }

    // 조건부 UPDATE: 주문 즉시 차감, RESERVED로 선점 후 TTL 내 미결제면 복구
    @Transactional
    public Long orderWithConditionalUpdate(Long buyerId, Long productId) {
        if (stockRepository.decreaseStock(productId) == 0) {
            throw new IllegalStateException("재고 부족");
        }
        Long orderId = saveOrder(buyerId, productId, OrderStatus.RESERVED,
                LocalDateTime.now().plus(reservationTtl));
        // 여기서 카프카로 바로 안 나감: 커밋 성공 후 AFTER_COMMIT 리스너가 발행 (유령 이벤트 방지)
        eventPublisher.publishEvent(new OrderCreatedEvent(orderId, productId));
        return orderId;
    }

    // 낙관적 락
    @Transactional
    public Long orderOptimisticOnce(Long buyerId, Long productId){
        Stock stock = stockRepository.findByProductId(productId).orElseThrow();
        stock.decrease();
        return saveOrder(buyerId, productId, OrderStatus.COMPLETED, null);
    }

    // 결제 mock 확정: 만료·타인 주문·중복 확정은 전이 쿼리의 WHERE가 0행으로 거부
    @Transactional
    public void confirmOrder(Long buyerId, Long orderId) {
        if (orderRepository.confirmReserved(orderId, buyerId, LocalDateTime.now()) == 0) {
            throw new IllegalStateException("확정할 수 없는 주문");
        }
    }

    // 만료 복구: 건별 트랜잭션: 전이+재고복구만 원자면 충분, 한 건 실패가 나머지 복구를 막지 않게
    @Transactional
    public boolean expireOrder(Long orderId, Long productId) {
        if (orderRepository.cancelReserved(orderId) == 0) {
            return false; // 그 사이 confirm이 이김: 복구 없음
        }
        stockRepository.increaseStock(productId);
        return true;
    }

    // 공통: 주문 저장
    private Long saveOrder(Long buyerId, Long productId, OrderStatus status, LocalDateTime expiresAt){
        Order order = orderRepository.save(Order.builder()
                .buyerId(buyerId).productId(productId)
                .status(status).expiresAt(expiresAt).build());
        return order.getId();
    }
}
