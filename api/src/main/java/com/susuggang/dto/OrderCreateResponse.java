package com.susuggang.dto;

import java.time.LocalDateTime;

public record OrderCreateResponse(Long orderId, LocalDateTime expiresAt) {
}
