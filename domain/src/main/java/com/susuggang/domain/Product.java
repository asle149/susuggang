package com.susuggang.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Builder
    private Product(String title, int price, Long sellerId, ProductStatus status) {
        this.title = title;
        this.price = price;
        this.sellerId = sellerId;
        this.status = status;
    }
}
