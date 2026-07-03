package com.susuggang.kafka;

import com.susuggang.domain.ProcessedOrder;
import com.susuggang.repository.ProcessedOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ProcessedOrderRepository processedOrderRepository;

    // at-least-once라 중복 배달이 정상 시나리오 — 장부(existsById)로 거르고,
    // 체크와 기록 사이 틈새 중복은 PK 제약이 최종 방어선(커밋 실패 → 재전달 → skip)
    @Transactional
    @KafkaListener(topics = "order-created", groupId = "susuggang-order")
    public void handle(OrderCreatedEvent event) {
        if (processedOrderRepository.existsById(event.orderId())) {
            log.info("skip (이미 처리): orderId={}", event.orderId());
            return;
        }
        log.info("알림 mock: 주문 완료 orderId={}, productId={}", event.orderId(), event.productId());
        processedOrderRepository.save(new ProcessedOrder(event.orderId()));
    }
}
