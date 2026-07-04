package com.susuggang.repository;

import com.susuggang.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);
}
