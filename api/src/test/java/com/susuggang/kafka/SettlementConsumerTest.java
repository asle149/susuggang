package com.susuggang.kafka;

import com.susuggang.domain.Product;
import com.susuggang.domain.ProductStatus;
import com.susuggang.repository.ProductRepository;
import com.susuggang.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SettlementConsumerTest {

    @Autowired SettlementConsumer consumer;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ProductRepository productRepository;

    Long productId;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        productRepository.deleteAll();

        productId = productRepository.save(Product.builder()
                .title("손뜨개 인형")
                .price(20000)
                .sellerId(1L)
                .status(ProductStatus.ON_SALE)
                .build()).getId();
    }

    @Test
    void 같은_확정_이벤트를_두_번_소비해도_정산은_한_건만_기록된다() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(777L, productId);

        consumer.handle(event);
        consumer.handle(event); // at-least-once 중복 배달 재현

        assertThat(settlementRepository.count()).isEqualTo(1);
        var settlement = settlementRepository.findById(777L).orElseThrow();
        assertThat(settlement.getAmount()).isEqualTo(20000);
        assertThat(settlement.getSellerId()).isEqualTo(1L);
        assertThat(settlement.getProductId()).isEqualTo(productId);
    }

    @Test
    void 상품이_없는_이벤트는_정산을_기록하지_않는다() {
        consumer.handle(new OrderConfirmedEvent(888L, 999_999L));

        assertThat(settlementRepository.count()).isZero();
    }
}
