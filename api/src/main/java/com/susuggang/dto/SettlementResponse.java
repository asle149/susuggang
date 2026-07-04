package com.susuggang.dto;

import java.time.LocalDateTime;

public record SettlementResponse(Long orderId, Long productId, int amount, LocalDateTime settledAt) {
}
