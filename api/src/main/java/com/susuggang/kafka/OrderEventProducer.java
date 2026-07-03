package com.susuggang.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    // 트랜잭션이 커밋된 뒤에만 호출됨 — 롤백이면 아예 안 불림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderCreatedEvent event) {
        try {
            // key = 상품ID → 같은 상품 이벤트는 같은 파티션 → 그 안에서 순서 보장
            kafkaTemplate.send("order-created", String.valueOf(event.productId()), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("order-created 발행 실패(유실 감수): {}", event, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("order-created 발행 실패(유실 감수): {}", event, e);
        }
    }
}
