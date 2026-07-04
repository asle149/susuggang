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

    // 이벤트가 2종이 되어 값 타입을 Object로 — JsonSerializer가 타입 헤더를 실어 컨슈머가 구분한다
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // 트랜잭션이 커밋된 뒤에만 호출됨 — 롤백이면 아예 안 불림
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderCreatedEvent event) {
        // key = 상품ID → 같은 상품 이벤트는 같은 파티션 → 그 안에서 순서 보장
        send("order-created", String.valueOf(event.productId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishConfirmed(OrderConfirmedEvent event) {
        send("order-confirmed", String.valueOf(event.productId()), event);
    }

    private void send(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("{} 발행 실패(유실 감수): {}", topic, event, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("{} 발행 실패(유실 감수): {}", topic, event, e);
        }
    }
}
