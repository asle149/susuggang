package com.susuggang.domain;

public enum PaymentStatus {
    REQUESTED,      // 승인 요청을 보냈고 결과를 모름 — 잔류 시 미확정 결제 추적 대상
    APPROVED,
    FAILED,
    CANCEL_PENDING, // 보상 취소 시도 중 — 잔류 시 취소 실패한 결제 추적 대상
    CANCELED,       // 보상 취소 완료 (돈이 나갔다가 돌아옴 — FAILED와 구분)
    CANCEL_FAILED   // 보상 취소 재투입 상한 초과 — 자동 복구를 멈추고 사람이 처리하는 끝 상태
}
