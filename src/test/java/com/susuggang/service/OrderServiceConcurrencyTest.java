package com.susuggang.service;

import com.susuggang.domain.Product;
import com.susuggang.domain.ProductStatus;
import com.susuggang.domain.Stock;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.ProductRepository;
import com.susuggang.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceConcurrencyTest {

    @Autowired OrderService orderService;
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

        stockRepository.save(Stock.builder()
                .productId(productId)
                .quantity(100)
                .build());
    }

    @Test
    void 비관락_동시에_200명이_주문해도_재고_100개까지만_팔린다() throws InterruptedException {
        int requests = 200;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(requests);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < requests; i++) {
            executor.submit(() -> {
                try {
                    orderService.orderWithLock(1L, productId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        int remaining = stockRepository.findByProductId(productId).orElseThrow().getQuantity();

        assertThat(success.get()).isEqualTo(100);
        assertThat(failed.get()).isEqualTo(100);
        assertThat(remaining).isEqualTo(0);              // 음수면 oversell
        assertThat(orderRepository.count()).isEqualTo(100);
    }

    @Test
    void 조건부업뎃_동시에_200명이_주문해도_재고_100개까지만_팔린다() throws InterruptedException {
        int requests = 200;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(requests);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < requests; i++) {
            executor.submit(() -> {
                try {
                    orderService.orderWithConditionalUpdate(1L, productId);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        int remaining = stockRepository.findByProductId(productId).orElseThrow().getQuantity();

        assertThat(success.get()).isEqualTo(100);
        assertThat(failed.get()).isEqualTo(100);
        assertThat(remaining).isEqualTo(0);              // 음수면 oversell
        assertThat(orderRepository.count()).isEqualTo(100);
    }
}
