package com.susuggang.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 결제 시도 장부 — 토스로 실제 나간 승인 요청만 기록한다 (주문 1 : 시도 N)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    private static final int FAIL_REASON_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    // 토스 세계의 식별자 — 취소·조회 때 내미는 값. unique로 같은 시도의 이중 기록을 막는다
    @Column(nullable = false, unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private String tossOrderId;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(length = FAIL_REASON_MAX)
    private String failReason;

    // 잔류 스캔 재투입 횟수 — 상한 판정 근거. 기존 행이 있는 DB에 ddl-auto가 컬럼을 추가할 수 있게 default 명시
    @Column(nullable = false, columnDefinition = "integer default 0 not null")
    private int cancelRetryCount;

    // 토스 응답의 승인 시각 원문(ISO-8601, 오프셋 포함) 보존
    private String approvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Payment(Long orderId, String paymentKey, String tossOrderId, int amount) {
        this.orderId = orderId;
        this.paymentKey = paymentKey;
        this.tossOrderId = tossOrderId;
        this.amount = amount;
        this.status = PaymentStatus.REQUESTED;
        this.createdAt = LocalDateTime.now();
    }

    public static Payment request(Long orderId, String paymentKey, String tossOrderId, int amount) {
        return new Payment(orderId, paymentKey, tossOrderId, amount);
    }

    public void approve(String approvedAt) {
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = approvedAt;
    }

    public void cancelPending() {
        this.status = PaymentStatus.CANCEL_PENDING;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELED;
    }

    public void increaseCancelRetry() {
        this.cancelRetryCount++;
    }

    public void failCancel() {
        this.status = PaymentStatus.CANCEL_FAILED;
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failReason = reason != null && reason.length() > FAIL_REASON_MAX
                ? reason.substring(0, FAIL_REASON_MAX)
                : reason;
    }
}
