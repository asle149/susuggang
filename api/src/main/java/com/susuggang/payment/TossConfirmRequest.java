package com.susuggang.payment;

// 토스 승인 API 요청 바디 — orderId는 우리 PK가 아니라 결제창에 넘겼던 토스용 주문번호(문자열)
public record TossConfirmRequest(String paymentKey, String orderId, Long amount) {
}
