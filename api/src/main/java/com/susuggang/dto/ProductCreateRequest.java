package com.susuggang.dto;

// sellerId는 요청 본문에서 받지 않는다 — 신원은 토큰에서 (@LoginMember). 본문 주장은 조작 가능
public record ProductCreateRequest(String title, int price, int quantity) {
}
