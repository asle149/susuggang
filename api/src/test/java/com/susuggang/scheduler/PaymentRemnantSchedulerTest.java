package com.susuggang.scheduler;

import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 상한 판정은 브로커 없이 검증 가능한 순수 로직 — 재투입 후 복구 경로는 PaymentCompensationConsumerTest가 커버
@ExtendWith(MockitoExtension.class)
class PaymentRemnantSchedulerTest {

    private static final String TOPIC = "payment-compensation-test";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private PaymentRemnantScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "compensationTopic", TOPIC);
    }

    @Test
    void 상한_미달_잔류는_카운트를_올리고_재투입한다() {
        Payment payment = Payment.request(1L, "pay_cap_under", "toss-cap", 20000);
        payment.cancelPending();
        stubScan(payment);

        scheduler.scanRemnants(LocalDateTime.now());

        assertThat(payment.getCancelRetryCount()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_PENDING);
        verify(kafkaTemplate).send(eq(TOPIC), anyString(), any());
    }

    @Test
    void 상한_도달_잔류는_CANCEL_FAILED로_전이하고_재투입하지_않는다() {
        Payment payment = Payment.request(1L, "pay_cap_over", "toss-cap", 20000);
        payment.cancelPending();
        payment.increaseCancelRetry();
        payment.increaseCancelRetry();
        payment.increaseCancelRetry();
        stubScan(payment);

        scheduler.scanRemnants(LocalDateTime.now());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_FAILED);
        verify(paymentRepository).save(payment);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    private void stubScan(Payment pending) {
        when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.CANCEL_PENDING), any()))
                .thenReturn(List.of(pending));
        when(paymentRepository.findByStatusAndCreatedAtBefore(eq(PaymentStatus.REQUESTED), any()))
                .thenReturn(List.of());
    }
}
