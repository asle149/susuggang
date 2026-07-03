package com.susuggang.kafka;

public record OrderCreatedEvent(Long orderId, Long productId) {
}
