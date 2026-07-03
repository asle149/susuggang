package com.susuggang.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventConsumer {

    @KafkaListener(topics = "order-created", groupId = "susuggang-order")
    public void handle(OrderCreatedEvent event) {
        log.info("consumed: orderId={}, productId={}", event.orderId(), event.productId());
    }
}
