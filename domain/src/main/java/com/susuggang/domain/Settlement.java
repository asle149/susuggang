package com.susuggang.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 정산 장부 — orderId가 PK라 중복 기록 시도가 제약 위반으로 막힌다 (멱등 2차 방어)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private LocalDateTime settledAt;

    public Settlement(Long orderId, Long productId, Long sellerId, int amount) {
        this.orderId = orderId;
        this.productId = productId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.settledAt = LocalDateTime.now();
    }
}
