package com.susuggang.dto;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String message, LocalDateTime createdAt) {
}
