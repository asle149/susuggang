package com.susuggang.domain;

public enum PaymentStatus {
    REQUESTED, // 승인 요청을 보냈고 결과를 모름 — 잔류 시 미확정 결제 추적 대상
    APPROVED,
    FAILED
}
