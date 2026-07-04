package com.susuggang.kafka;

// buyerId: 알림 수신자 — 필드 추가 전 발행분(null)은 컨슈머가 알림만 생략
public record OrderCreatedEvent(Long orderId, Long productId, Long buyerId) {
}
