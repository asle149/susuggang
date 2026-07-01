package com.susuggang.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders") // order는 예약어
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private LocalDateTime expiresAt;

    @Builder
    private Order(Long buyerId, Long productId, OrderStatus status, LocalDateTime expiresAt) {
        this.buyerId = buyerId;
        this.productId = productId;
        this.status = status;
        this.expiresAt = expiresAt;
    }
}
