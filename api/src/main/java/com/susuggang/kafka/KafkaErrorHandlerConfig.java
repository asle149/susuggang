package com.susuggang.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

    // 재시도 소진 메시지를 DLQ 토픽(원토픽-dlt, 같은 파티션)으로 옮기는 담당 — 발행 도구는 기존 KafkaTemplate 재사용
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate);
    }

    // 컨슈머가 예외를 던지면 1초 간격 3회 재시도(총 4회 시도), 소진 시 recoverer가 DLQ로.
    // 부트가 CommonErrorHandler 타입 빈을 찾아 리스너 컨테이너에 자동 적용한다
    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
