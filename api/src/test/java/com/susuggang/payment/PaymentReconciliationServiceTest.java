package com.susuggang.payment;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.PaymentRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long ORDER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final String PAYMENT_KEY = "pay_reconcile";
    private static final String TOSS_ORDER_ID = "toss-order";
    private static final int AMOUNT = 20000;
    private static final String APPROVED_AT = "2026-08-22T12:00:00+09:00";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TossPaymentClient tossPaymentClient;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentCompensationService compensationService;
    @InjectMocks
    private PaymentReconciliationService reconciliationService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = Payment.request(ORDER_ID, PAYMENT_KEY, TOSS_ORDER_ID, AMOUNT);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
    }

    @Test
    void DONE이면_조건부_승인_후_주문을_확정한다() {
        Order order = order();
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("DONE"));
        when(paymentRepository.settleRequested(
                PAYMENT_ID, PaymentStatus.APPROVED, APPROVED_AT, null)).thenReturn(1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.APPROVED);
        verify(paymentRepository).settleRequested(
                PAYMENT_ID, PaymentStatus.APPROVED, APPROVED_AT, null);
        verify(paymentService).confirmOrCompensate(payment, BUYER_ID);
    }

    @Test
    void DONE인데_조건부_승인에_실패하면_후속_처리를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("DONE"));
        when(paymentRepository.settleRequested(
                PAYMENT_ID, PaymentStatus.APPROVED, APPROVED_AT, null)).thenReturn(0);

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentService, never()).confirmOrCompensate(any(), any());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void DONE인데_주문이_없으면_조건부_승인_후_보상을_요청한다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("DONE"));
        when(paymentRepository.settleRequested(
                PAYMENT_ID, PaymentStatus.APPROVED, APPROVED_AT, null)).thenReturn(1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.APPROVED);
        verify(paymentRepository).settleRequested(
                PAYMENT_ID, PaymentStatus.APPROVED, APPROVED_AT, null);
        verify(compensationService).requestCancel(PAYMENT_ID);
    }

    @Test
    void DONE인데_주문_확정이_불가능하면_예외를_전파하지_않는다() {
        Order order = order();
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("DONE"));
        when(paymentRepository.settleRequested(
                PAYMENT_ID, PaymentStatus.APPROVED, APPROVED_AT, null)).thenReturn(1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        doThrow(new BusinessException(ErrorCode.ORDER_NOT_CONFIRMABLE))
                .when(paymentService).confirmOrCompensate(payment, BUYER_ID);

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.APPROVED);
    }

    @Test
    void DONE인데_예상하지_못한_예외면_전파한다() {
        Order order = order();
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("DONE"));
        when(paymentRepository.settleRequested(
                PAYMENT_ID, PaymentStatus.APPROVED, APPROVED_AT, null)).thenReturn(1);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        doThrow(new IllegalStateException("save failed"))
                .when(paymentService).confirmOrCompensate(payment, BUYER_ID);

        assertThatThrownBy(() -> reconciliationService.reconcile(payment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");
    }

    @Test
    void ABORTED이면_조건부로_FAILED를_기록한다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("ABORTED"));
        when(paymentRepository.settleRequested(
                PAYMENT_ID, PaymentStatus.FAILED, null, "toss status=ABORTED")).thenReturn(1);

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.FAILED);
        verify(paymentRepository).settleRequested(
                PAYMENT_ID, PaymentStatus.FAILED, null, "toss status=ABORTED");
    }

    @Test
    void CANCELED이면_조건부로_CANCELED를_기록한다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("CANCELED"));
        when(paymentRepository.settleRequested(
                PAYMENT_ID, PaymentStatus.CANCELED, null, null)).thenReturn(1);

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.CANCELED);
        verify(paymentRepository).settleRequested(
                PAYMENT_ID, PaymentStatus.CANCELED, null, null);
    }

    @Test
    void READY이면_조건부_전이를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("READY"));

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentRepository, never()).settleRequested(any(), any(), any(), any());
    }

    @Test
    void 조회가_실패하면_조건부_전이를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenThrow(findNotFound());

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentRepository, never()).settleRequested(any(), any(), any(), any());
    }

    @Test
    void PARTIAL_CANCELED이면_자동_전이를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(response("PARTIAL_CANCELED"));

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentRepository, never()).settleRequested(any(), any(), any(), any());
    }

    @Test
    void paymentKey가_다르면_자동_전이를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(
                response("other-payment-key", TOSS_ORDER_ID, "DONE", (long) AMOUNT));

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentRepository, never()).settleRequested(any(), any(), any(), any());
    }

    @Test
    void tossOrderId가_다르면_자동_전이를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(
                response(PAYMENT_KEY, "other-order-id", "DONE", (long) AMOUNT));

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentRepository, never()).settleRequested(any(), any(), any(), any());
    }

    @Test
    void DONE인데_금액이_다르면_자동_전이를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(
                response(PAYMENT_KEY, TOSS_ORDER_ID, "DONE", 10000L));

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentRepository, never()).settleRequested(any(), any(), any(), any());
    }

    @Test
    void 조회_응답이_null이면_자동_전이를_하지_않는다() {
        when(tossPaymentClient.find(PAYMENT_KEY)).thenReturn(null);

        PaymentReconciliationService.Result result = reconciliationService.reconcile(payment);

        assertThat(result).isEqualTo(PaymentReconciliationService.Result.UNRESOLVED);
        verify(paymentRepository, never()).settleRequested(any(), any(), any(), any());
    }

    private Order order() {
        return Order.builder()
                .buyerId(BUYER_ID)
                .productId(3L)
                .status(OrderStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    private TossPaymentResponse response(String status) {
        return response(PAYMENT_KEY, TOSS_ORDER_ID, status, (long) AMOUNT);
    }

    private TossPaymentResponse response(String paymentKey, String orderId, String status, Long amount) {
        return new TossPaymentResponse(paymentKey, orderId, status, "간편결제", amount, APPROVED_AT);
    }

    private FeignException findNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/v1/payments/x",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new FeignException.NotFound("not found", request, null, Map.of());
    }
}
