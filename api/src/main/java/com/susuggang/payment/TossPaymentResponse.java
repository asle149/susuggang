package com.susuggang.payment;

// 토스 Payment 객체 중 지금 쓰는 필드만 — 나머지는 Jackson이 무시
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        String method,
        Long totalAmount,
        String approvedAt) {
}
