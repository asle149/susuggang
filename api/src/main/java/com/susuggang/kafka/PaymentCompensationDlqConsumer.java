package com.susuggang.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentCompensationDlqConsumer {

    // 주 컨슈머와 그룹 분리 — 같은 그룹이면 리밸런싱·오프셋 관리가 한 팀으로 묶여 서로 흔든다.
    // 기록만 남기고(키바나 발견용) 상태는 CANCEL_PENDING 그대로 두어 잔류 스캔이 2차 안전망으로 남는다.
    // 토픽 접미사 -dlt는 spring-kafka 3.x DLQ 발행기의 기본 규약
    @KafkaListener(topics = "#{'${payment.compensation-topic:payment-compensation}' + '-dlt'}",
            groupId = "susuggang-compensation-dlq")
    public void handle(PaymentCancelRequestedEvent event) {
        log.error("보상 DLQ 격리: paymentId={}", event.paymentId());
    }
}
