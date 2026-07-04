package com.susuggang.kafka;

import com.susuggang.domain.Product;
import com.susuggang.domain.Settlement;
import com.susuggang.repository.ProductRepository;
import com.susuggang.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementConsumer {

    private final SettlementRepository settlementRepository;
    private final ProductRepository productRepository;

    // 알림 컨슈머와 같은 멱등 2겹 — 정산 장부 자체가 처리 기록.
    // 그룹을 분리해 알림 쪽과 오프셋·장애가 서로 격리된다
    @Transactional
    @KafkaListener(topics = "order-confirmed", groupId = "susuggang-settlement")
    public void handle(OrderConfirmedEvent event) {
        if (settlementRepository.existsById(event.orderId())) {
            log.info("skip (이미 정산): orderId={}", event.orderId());
            return;
        }
        Product product = productRepository.findById(event.productId()).orElse(null);
        if (product == null) {
            // 판매자·금액을 알 수 없으면 기록 불가 — 재전달돼도 같은 결과라 멱등은 유지
            log.warn("정산 스킵(상품 없음): orderId={}, productId={}", event.orderId(), event.productId());
            return;
        }
        settlementRepository.save(new Settlement(
                event.orderId(), event.productId(), product.getSellerId(), product.getPrice()));
        log.info("정산 기록: orderId={}, sellerId={}, amount={}",
                event.orderId(), product.getSellerId(), product.getPrice());
    }
}
