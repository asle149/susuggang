package com.susuggang.payment;

public record PaymentConfirmContext(Long orderId, Long amount, int price) {
}
