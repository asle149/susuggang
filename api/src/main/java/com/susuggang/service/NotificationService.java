package com.susuggang.service;

import com.susuggang.dto.NotificationResponse;
import com.susuggang.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMine(Long buyerId) {
        return notificationRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
