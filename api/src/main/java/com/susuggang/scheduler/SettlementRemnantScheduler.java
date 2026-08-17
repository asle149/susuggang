package com.susuggang.scheduler;

import com.susuggang.domain.Order;
import com.susuggang.kafka.OrderConfirmedEvent;
import com.susuggang.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 보상 잔류 스캔과 같은 패턴: 커밋 후 발행이 유실돼도 DB 상태(정산 없는 COMPLETED)가 재발행 큐다.
// 정산 기록은 멱등(orderId PK)이라 재발행에 상한이 필요 없다 — 외부 호출·돈 이동이 없는 경로
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementRemnantScheduler {

    private static final Duration REMNANT_THRESHOLD = Duration.ofMinutes(10);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(initialDelay = 600_000, fixedDelay = 600_000)
    public void scanRemnants() {
        scanRemnants(LocalDateTime.now());
    }

    public void scanRemnants(LocalDateTime now) {
        List<Order> remnants = orderRepository.findSettlementRemnants(now.minus(REMNANT_THRESHOLD));
        for (Order order : remnants) {
            // 실행은 정산 컨슈머 단일 경로 유지 — 키도 원 발행(상품ID)과 동일하게
            kafkaTemplate.send("order-confirmed", String.valueOf(order.getProductId()),
                    new OrderConfirmedEvent(order.getId(), order.getProductId()));
        }
        if (!remnants.isEmpty()) {
            log.warn("정산 잔류 재발행: {}건", remnants.size());
        }
    }
}
