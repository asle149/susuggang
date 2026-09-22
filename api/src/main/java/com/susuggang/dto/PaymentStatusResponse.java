package com.susuggang.dto;

import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.PaymentStatus;

public record PaymentStatusResponse(Long orderId, Status status) {

    public enum Status {
        NONE, PENDING, APPROVED, FAILED, CANCELED
    }

    // 장부 상태를 사용자 관점으로 접는다 — APPROVED여도 주문이 아직 확정 전이면 결과가 아니므로 PENDING
    public static PaymentStatusResponse of(Long orderId, PaymentStatus payment, OrderStatus order) {
        Status status = switch (payment) {
            case REQUESTED -> Status.PENDING;
            case APPROVED -> order == OrderStatus.COMPLETED ? Status.APPROVED : Status.PENDING;
            case FAILED -> Status.FAILED;
            case CANCEL_PENDING, CANCELED, CANCEL_FAILED -> Status.CANCELED;
        };
        return new PaymentStatusResponse(orderId, status);
    }

    public static PaymentStatusResponse none(Long orderId) {
        return new PaymentStatusResponse(orderId, Status.NONE);
    }
}
