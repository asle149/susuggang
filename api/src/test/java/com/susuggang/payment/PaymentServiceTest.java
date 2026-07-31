package com.susuggang.payment;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.domain.Product;
import com.susuggang.domain.ProductStatus;
import com.susuggang.domain.Stock;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.PaymentRepository;
import com.susuggang.repository.ProductRepository;
import com.susuggang.repository.StockRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentServiceTest {

    private static final int PRICE = 20000;

    @Autowired PaymentService paymentService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @Autowired StockRepository stockRepository;

    @MockitoBean TossPaymentClient tossPaymentClient;

    Long productId;
    Long orderId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        stockRepository.deleteAll();
        productRepository.deleteAll();

        Product product = productRepository.save(Product.builder()
                .title("손뜨개 인형")
                .price(PRICE)
                .sellerId(1L)
                .status(ProductStatus.ON_SALE)
                .build());
        productId = product.getId();

        stockRepository.save(Stock.builder()
                .productId(productId)
                .quantity(0)
                .build());

        orderId = orderRepository.save(Order.builder()
                .buyerId(1L).productId(productId)
                .status(OrderStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build()).getId();
    }

    private TossPaymentResponse approvedResponse(String paymentKey) {
        return new TossPaymentResponse(paymentKey, "toss-1", "DONE", "간편결제",
                (long) PRICE, "2026-07-30T21:00:00+09:00");
    }

    private FeignException tossRejection() {
        Request request = Request.create(Request.HttpMethod.POST, "/v1/payments/confirm",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("Not Found", request,
                "{\"code\":\"NOT_FOUND_PAYMENT_SESSION\"}".getBytes(StandardCharsets.UTF_8), Map.of());
    }

    @Test
    void 승인_성공이면_APPROVED_기록과_주문_확정() {
        given(tossPaymentClient.confirm(any())).willReturn(approvedResponse("pay_ok"));

        paymentService.confirmPayment(1L, orderId, "toss-1", "pay_ok", (long) PRICE);

        Payment payment = paymentRepository.findByPaymentKey("pay_ok").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isNotNull();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void 토스가_거절하면_FAILED_기록과_주문은_그대로() {
        willThrow(tossRejection()).given(tossPaymentClient).confirm(any());

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, orderId, "toss-1", "pay_reject", (long) PRICE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_CONFIRM_FAILED);

        Payment payment = paymentRepository.findByPaymentKey("pay_reject").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailReason()).contains("NOT_FOUND_PAYMENT_SESSION");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void 응답이_없으면_REQUESTED로_남는다() {
        // 타임아웃·커넥션 실패 = 토스 응답이 없어 status가 음수인 FeignException
        FeignException noResponse = mock(FeignException.class);
        when(noResponse.status()).thenReturn(-1);
        when(noResponse.contentUTF8()).thenReturn("");
        willThrow(noResponse).given(tossPaymentClient).confirm(any());

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, orderId, "toss-1", "pay_timeout", (long) PRICE))
                .isInstanceOf(BusinessException.class);

        Payment payment = paymentRepository.findByPaymentKey("pay_timeout").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    void 금액_불일치면_기록도_토스_호출도_없다() {
        assertThatThrownBy(() -> paymentService.confirmPayment(1L, orderId, "toss-1", "pay_tamper", 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(paymentRepository.count()).isZero();
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    void 같은_paymentKey_재시도는_새_행을_만들지_않는다() {
        willThrow(tossRejection()).given(tossPaymentClient).confirm(any());

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, orderId, "toss-1", "pay_retry", (long) PRICE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> paymentService.confirmPayment(1L, orderId, "toss-1", "pay_retry", (long) PRICE))
                .isInstanceOf(BusinessException.class);

        assertThat(paymentRepository.count()).isEqualTo(1);
    }
}
