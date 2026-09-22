package com.susuggang.dto;

// 결제사 API 관례대로 미확정도 에러가 아니라 상태로 돌려준다 — PENDING이면 클라이언트는 재결제 대신 상태 조회로 기다린다
public record PaymentConfirmResponse(Status status, Long orderId, String paymentKey, String approvedAt) {

    public enum Status {
        APPROVED, PENDING
    }

    public static PaymentConfirmResponse approved(Long orderId, String paymentKey, String approvedAt) {
        return new PaymentConfirmResponse(Status.APPROVED, orderId, paymentKey, approvedAt);
    }

    public static PaymentConfirmResponse pending(Long orderId, String paymentKey) {
        return new PaymentConfirmResponse(Status.PENDING, orderId, paymentKey, null);
    }
}
