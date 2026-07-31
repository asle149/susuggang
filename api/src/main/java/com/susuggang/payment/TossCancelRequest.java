package com.susuggang.payment;

// 토스 취소 API 필수 필드 — cancelReason 하나 (부분 취소 등은 스코프 밖)
public record TossCancelRequest(String cancelReason) {
}
