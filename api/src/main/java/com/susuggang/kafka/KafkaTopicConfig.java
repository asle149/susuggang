package com.susuggang.kafka;

import org.apache.kafka.clients.admin.NewTopic;
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
}
