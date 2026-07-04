package com.susuggang.scheduler;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.repository.OrderRepository;
import com.susuggang.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000)
    public void expireOverdueReservations() {
        List<Order> overdue = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.RESERVED, LocalDateTime.now());
        if (overdue.isEmpty()) {
            return;
        }

        int recovered = 0;
        for (Order order : overdue) {
            if (orderService.expireOrder(order.getId(), order.getProductId())) {
                recovered++;
            }
        }
        log.info("예약 만료 처리: 대상 {}건, 복구 {}건", overdue.size(), recovered);
    }
}
