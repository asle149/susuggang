package com.susuggang.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 멱등 소비 장부 — orderId가 PK라 중복 기록 시도가 제약 위반으로 막힌다
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedOrder {

    @Id
    private Long orderId;

    public ProcessedOrder(Long orderId) {
        this.orderId = orderId;
    }
}
