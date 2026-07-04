package com.susuggang.kafka;

public record OrderConfirmedEvent(Long orderId, Long productId) {
}
