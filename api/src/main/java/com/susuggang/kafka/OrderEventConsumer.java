package com.susuggang.kafka;

import com.susuggang.domain.Notification;
import com.susuggang.domain.ProcessedOrder;
import com.susuggang.domain.Product;
import com.susuggang.repository.NotificationRepository;
import com.susuggang.repository.ProcessedOrderRepository;
import com.susuggang.repository.ProductRepository;
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
    private final NotificationRepository notificationRepository;
    private final ProductRepository productRepository;

    // at-least-once라 중복 배달이 정상 시나리오 — 장부(existsById)로 거르고,
    // 체크와 기록 사이 틈새 중복은 PK 제약이 최종 방어선(커밋 실패 → 재전달 → skip)
    @Transactional
    @KafkaListener(topics = "order-created", groupId = "susuggang-order")
    public void handle(OrderCreatedEvent event) {
        if (processedOrderRepository.existsById(event.orderId())) {
            log.info("skip (이미 처리): orderId={}", event.orderId());
            return;
        }
        processedOrderRepository.save(new ProcessedOrder(event.orderId()));

        if (event.buyerId() == null) {
            // buyerId 필드 추가 전 발행분 — 수신자를 몰라 알림만 생략
            log.warn("알림 생략(buyerId 없음): orderId={}", event.orderId());
            return;
        }
        String title = productRepository.findById(event.productId())
                .map(Product::getTitle)
                .orElse("작품");
        notificationRepository.save(new Notification(event.buyerId(),
                "'" + title + "' 주문이 접수되었습니다. 기한 내 결제를 완료해 주세요."));
        log.info("알림 저장: orderId={}, buyerId={}", event.orderId(), event.buyerId());
    }
}
