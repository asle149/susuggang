package com.susuggang.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.compensation-topic:payment-compensation}")
    private String compensationTopic;

    // CANCEL_PENDING 커밋 후에만 발행 — 커밋 전에 나가면 기록 없는 취소 요청(유령 이벤트)이 된다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCancelRequested(PaymentCancelRequestedEvent event) {
        try {
            // key = 결제ID → 같은 결제 건 이벤트는 같은 파티션에서 순서 보장
            kafkaTemplate.send(compensationTopic, String.valueOf(event.paymentId()), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("payment-compensation 발행 실패 — CANCEL_PENDING 잔류 스캔이 안전망: {}", event, ex);
                        }
                    });
        } catch (Exception e) {
            log.error("payment-compensation 발행 실패 — CANCEL_PENDING 잔류 스캔이 안전망: {}", event, e);
        }
    }
}
