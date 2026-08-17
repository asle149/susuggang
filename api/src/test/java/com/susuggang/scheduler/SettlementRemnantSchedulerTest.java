package com.susuggang.scheduler;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.kafka.OrderConfirmedEvent;
import com.susuggang.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 발견·재발행만 검증하는 순수 로직 — 정산 기록의 멱등은 SettlementConsumerTest가 커버
@ExtendWith(MockitoExtension.class)
class SettlementRemnantSchedulerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private SettlementRemnantScheduler scheduler;

    @Test
    void 정산_없는_확정_주문은_order_confirmed로_재발행한다() {
        Order order = Order.builder()
                .buyerId(1L).productId(7L).status(OrderStatus.COMPLETED)
                .expiresAt(LocalDateTime.now().minusHours(1)).build();
        ReflectionTestUtils.setField(order, "id", 42L);
        when(orderRepository.findSettlementRemnants(any())).thenReturn(List.of(order));

        scheduler.scanRemnants(LocalDateTime.now());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq("order-confirmed"),
                org.mockito.ArgumentMatchers.eq("7"), captor.capture());
        OrderConfirmedEvent event = (OrderConfirmedEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(42L);
        assertThat(event.productId()).isEqualTo(7L);
    }

    @Test
    void 잔류가_없으면_발행하지_않는다() {
        when(orderRepository.findSettlementRemnants(any())).thenReturn(List.of());

        scheduler.scanRemnants(LocalDateTime.now());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
