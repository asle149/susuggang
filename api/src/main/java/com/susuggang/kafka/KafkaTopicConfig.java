package com.susuggang.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // 자동 생성에 맡기면 파티션 1개 — 상품ID 키 분배를 보려면 명시 생성
    @Bean
    public NewTopic orderCreated() {
        return TopicBuilder.name("order-created").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderConfirmed() {
        return TopicBuilder.name("order-confirmed").partitions(3).replicas(1).build();
    }

    // 토픽명은 프로퍼티로 — 테스트가 전용 토픽으로 격리할 수 있게 (컨텍스트 캐시 간 같은 그룹 크로스톡 방지)
    @Bean
    public NewTopic paymentCompensation(
            @Value("${payment.compensation-topic:payment-compensation}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
