package com.susuggang.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 임시 핑퐁 확인용 — 로컬 카프카(docker compose)가 떠 있어야 한다
@SpringBootTest
class KafkaPingTest {

    @Autowired OrderEventProducer producer;

    @Test
    void 발행하면_컨슈머_로그가_찍힌다() throws InterruptedException {
        producer.publish(new OrderCreatedEvent(1L, 1L, 1L));
        producer.publish(new OrderCreatedEvent(10L, 1L, 1L));
        producer.publish(new OrderCreatedEvent(11L, 1L, 1L));
        producer.publish(new OrderCreatedEvent(12L, 2L, 1L));
        producer.publish(new OrderCreatedEvent(13L, 3L, 1L));

        Thread.sleep(5000);
    }
}
