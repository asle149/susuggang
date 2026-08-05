package com.susuggang.kafka;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.payment.TossPaymentClient;
import com.susuggang.repository.PaymentRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 로컬 카프카(docker compose)가 떠 있어야 한다.
// 전용 토픽으로 격리 — 캐시된 다른 컨텍스트의 같은 그룹 컨슈머가 메시지를 가로채는 크로스톡 방지
@SpringBootTest(properties = "payment.compensation-topic=payment-compensation-test")
class PaymentCompensationConsumerTest {

    @Autowired
    private PaymentCompensationConsumer consumer;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${payment.compensation-topic:payment-compensation}")
    private String compensationTopic;
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

    // 재시도(1초 x 3회)가 다 실패하면 DLQ로 격리되고, 상태는 잔류 스캔이 복구할 수 있게 남아야 한다
    @Test
    void 취소가_계속_실패하면_DLQ로_격리되고_상태는_CANCEL_PENDING으로_남는다() throws InterruptedException {
        Logger dlqLogger = (Logger) LoggerFactory.getLogger(PaymentCompensationDlqConsumer.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        dlqLogger.addAppender(logs);

        try {
            Payment payment = Payment.request(1L, "pay_dlq_" + System.nanoTime(), "toss-dlq", 20000);
            payment.cancelPending();
            paymentRepository.save(payment);
            // mock(FeignException.class)은 스택트레이스 내부가 null이라 DLQ 발행기의 예외 헤더 기록에서
            // NPE가 난다 — 진짜 예외 인스턴스로 던져야 한다
            Request request = Request.create(Request.HttpMethod.POST, "/v1/payments/x/cancel",
                    Map.of(), null, StandardCharsets.UTF_8, null);
            when(tossPaymentClient.cancel(anyString(), any()))
                    .thenThrow(new FeignException.FeignServerException(500, "toss down", request, null, Map.of()));

            // 재시도·DLQ는 브로커를 거쳐야 돈다 — 직접 호출이 아니라 발행으로
            kafkaTemplate.send(compensationTopic, String.valueOf(payment.getId()),
                    new PaymentCancelRequestedEvent(payment.getId()));
            Thread.sleep(8000); // 최초 1회 + 재시도 3회(1초 간격) + DLQ 소비 여유

            long dlqLogCount = logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("DLQ") && m.contains(String.valueOf(payment.getId())))
                    .count();
            assertThat(dlqLogCount).isEqualTo(1);
            assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.CANCEL_PENDING);
        } finally {
            dlqLogger.detachAppender(logs);
        }
    }
}
