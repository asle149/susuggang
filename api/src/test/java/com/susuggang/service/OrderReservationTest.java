package com.susuggang.service;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Product;
import com.susuggang.domain.ProductStatus;
import com.susuggang.domain.Stock;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.ProductRepository;
import com.susuggang.repository.StockRepository;
import com.susuggang.scheduler.OrderExpirationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderReservationTest {

    @Autowired OrderService orderService;
    @Autowired OrderExpirationScheduler scheduler;
    @Autowired ProductRepository productRepository;
    @Autowired StockRepository stockRepository;
    @Autowired OrderRepository orderRepository;

    Long productId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();

        Product product = productRepository.save(Product.builder()
                .title("손뜨개 인형")
                .price(20000)
                .sellerId(1L)
                .status(ProductStatus.ON_SALE)
                .build());
        productId = product.getId();

        // 예약 주문이 이미 재고를 차감한 상태를 재현
        stockRepository.save(Stock.builder()
                .productId(productId)
                .quantity(0)
                .build());
    }

    private Long saveReserved(LocalDateTime expiresAt) {
        return orderRepository.save(Order.builder()
                .buyerId(1L).productId(productId)
                .status(OrderStatus.RESERVED).expiresAt(expiresAt)
                .build()).getId();
    }

    private int stockQuantity() {
        return stockRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    @Test
    void 만료된_예약은_스케줄러가_취소하고_재고를_복구한다() {
        Long orderId = saveReserved(LocalDateTime.now().minusMinutes(1));

        scheduler.expireOverdueReservations();

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELED);
        assertThat(stockQuantity()).isEqualTo(1);
    }

    @Test
    void 만료_전_confirm은_주문을_확정한다() {
        Long orderId = saveReserved(LocalDateTime.now().plusMinutes(10));

        orderService.confirmOrder(1L, orderId);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(stockQuantity()).isEqualTo(0);
    }

    @Test
    void 만료된_주문은_confirm이_거부된다() {
        Long orderId = saveReserved(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> orderService.confirmOrder(1L, orderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_CONFIRMABLE);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isNotEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void 타인의_주문은_confirm이_거부된다() {
        Long orderId = saveReserved(LocalDateTime.now().plusMinutes(10));

        assertThatThrownBy(() -> orderService.confirmOrder(2L, orderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_CONFIRMABLE);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void confirm된_주문은_만료_처리가_복구하지_않는다() {
        Long orderId = saveReserved(LocalDateTime.now().plusMinutes(10));
        orderService.confirmOrder(1L, orderId);

        // 스캔~전이 사이에 confirm이 끼어든 레이스 재현 — status 가드가 복구를 막아야 함
        boolean recovered = orderService.expireOrder(orderId, productId);

        assertThat(recovered).isFalse();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(stockQuantity()).isEqualTo(0);
    }
}
