package com.susuggang.kafka;

// 페이로드는 키만 — 가격 경합이 없는 도메인이라 스냅샷 대신 컨슈머가 시점 조회한다
public record PaymentCancelRequestedEvent(Long paymentId) {
}
