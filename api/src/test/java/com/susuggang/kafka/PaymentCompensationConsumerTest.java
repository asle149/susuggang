package com.susuggang.kafka;

import com.susuggang.domain.Payment;
import com.susuggang.payment.TossPaymentClient;
import com.susuggang.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class PaymentCompensationConsumerTest {

    @Autowired
    private PaymentCompensationConsumer consumer;
    @Autowired
    private PaymentRepository paymentRepository;
    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    // at-least-once 재전달 대비 — 이미 처리된 건은 취소 API가 다시 나가면 안 된다
    @Test
    void 이미_취소된_결제는_다시_취소하지_않는다() {
        // 키를 실행마다 다르게 — dev DB에 지난 실행 행이 남아도 unique 제약과 안 부딪히게
        Payment payment = Payment.request(1L, "pay_idem_" + System.nanoTime(), "toss-idem", 20000);
        payment.cancelPending();
        payment.cancel();
        paymentRepository.save(payment);

        consumer.handle(new PaymentCancelRequestedEvent(payment.getId()));

        verifyNoInteractions(tossPaymentClient);
    }
}
