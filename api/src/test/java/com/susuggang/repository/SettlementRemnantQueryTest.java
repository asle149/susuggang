package com.susuggang.repository;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.stream.Collectors.toSet;

@SpringBootTest
class SettlementRemnantQueryTest {

    @Autowired OrderRepository orderRepository;
    @Autowired SettlementRepository settlementRepository;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        orderRepository.deleteAll();
    }

    private Order saveOrder(OrderStatus status, LocalDateTime expiresAt) {
        return orderRepository.save(Order.builder()
                .buyerId(1L)
                .productId(1L)
                .status(status)
                .expiresAt(expiresAt)
                .build());
    }

    @Test
    void 오래된_확정_주문_중_정산되지_않은_주문만_조회한다() {
        LocalDateTime scanTime = LocalDateTime.now();
        LocalDateTime cutoff = scanTime.minusMinutes(10);
        LocalDateTime beforeCutoff = cutoff.minusSeconds(1);
        LocalDateTime afterCutoff = cutoff.plusSeconds(1);

        Order remnant = saveOrder(OrderStatus.COMPLETED, beforeCutoff);
        saveOrder(OrderStatus.COMPLETED, afterCutoff);
        saveOrder(OrderStatus.COMPLETED, null);
        saveOrder(OrderStatus.RESERVED, beforeCutoff);
        saveOrder(OrderStatus.CANCELED, beforeCutoff);
        Order settled = saveOrder(OrderStatus.COMPLETED, beforeCutoff);
        settlementRepository.save(new Settlement(
                settled.getId(), settled.getProductId(), 1L, 10000));

        Set<Long> remnantIds = orderRepository.findSettlementRemnants(cutoff).stream()
                .map(Order::getId)
                .collect(toSet());

        assertThat(remnantIds).containsExactly(remnant.getId());
    }
}
