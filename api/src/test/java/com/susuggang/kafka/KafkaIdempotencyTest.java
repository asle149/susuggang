package com.susuggang.kafka;

import com.susuggang.repository.ProcessedOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// 로컬 카프카(docker compose)가 떠 있어야 한다
@SpringBootTest
class KafkaIdempotencyTest {

    @Autowired OrderEventProducer producer;
    @Autowired ProcessedOrderRepository processedOrderRepository;

    @Test
    void 같은_이벤트를_두번_발행해도_처리는_한번이다() throws InterruptedException {
        Long orderId = System.currentTimeMillis();   // 실행마다 새 주문ID

        producer.publish(new OrderCreatedEvent(orderId, 1L));
        producer.publish(new OrderCreatedEvent(orderId, 1L));
        Thread.sleep(5000);

        assertThat(processedOrderRepository.existsById(orderId)).isTrue();
    }
}
