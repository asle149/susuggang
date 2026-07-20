package com.susuggang.dto;

public record PaymentConfirmRequest(Long orderId, String tossOrderId, String paymentKey, Long amount) {
}
