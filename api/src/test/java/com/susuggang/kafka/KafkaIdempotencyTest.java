package com.susuggang.kafka;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.susuggang.repository.ProcessedOrderRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        // 컨슈머 로그를 리스트로 수집 — "효과 1번"을 눈이 아니라 단언으로 검증
        Logger consumerLogger = (Logger) LoggerFactory.getLogger(OrderEventConsumer.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        consumerLogger.addAppender(logs);

        try {
            Long orderId = System.currentTimeMillis();   // 실행마다 새 주문ID
            producer.publish(new OrderCreatedEvent(orderId, 1L, 1L));
            producer.publish(new OrderCreatedEvent(orderId, 1L, 1L));
            Thread.sleep(5000);

            assertThat(count(logs, "알림 저장", orderId)).isEqualTo(1);   // 도착 2번이어도 효과는 1번
            assertThat(count(logs, "skip", orderId)).isEqualTo(1);        // 두 번째는 스킵
            assertThat(processedOrderRepository.existsById(orderId)).isTrue();
        } finally {
            consumerLogger.detachAppender(logs);
        }
    }

    private long count(ListAppender<ILoggingEvent> logs, String keyword, Long orderId) {
        return logs.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains(keyword) && m.contains(String.valueOf(orderId)))
                .count();
    }
}
