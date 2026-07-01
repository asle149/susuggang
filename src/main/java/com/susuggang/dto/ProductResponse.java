package com.susuggang.dto;

import com.susuggang.domain.Product;
import com.susuggang.domain.Stock;

public record ProductResponse(Long id, String title, int price, Long sellerId, String status, int quantity) {

    public static ProductResponse from(Product product, Stock stock) {
        return new ProductResponse(
                product.getId(),
                product.getTitle(),
                product.getPrice(),
                product.getSellerId(),
                product.getStatus().name(),
                stock.getQuantity());
    }
}
