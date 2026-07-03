package com.susuggang.dto;

public record ProductCreateRequest(String title, int price, Long sellerId, int quantity) {
}
