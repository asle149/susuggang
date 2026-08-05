package com.susuggang.kafka;

import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.payment.TossCancelRequest;
import com.susuggang.payment.TossPaymentClient;
import com.susuggang.repository.PaymentRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompensationConsumer {

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;

    // @Transactional 없음 — 외부 취소 왕복을 트랜잭션이 물면 안 되는 원칙은 컨슈머에서도 동일
    @KafkaListener(topics = "${payment.compensation-topic:payment-compensation}", groupId = "susuggang-compensation")
    public void handle(PaymentCancelRequestedEvent event) {
        Payment payment = paymentRepository.findById(event.paymentId()).orElse(null);
        // 재전달(at-least-once) 멱등 가드 — CANCEL_PENDING이 아니면 이미 처리된 건
        if (payment == null || payment.getStatus() != PaymentStatus.CANCEL_PENDING) {
            log.info("skip (보상 대상 아님): paymentId={}", event.paymentId());
            return;
        }
        try {
            tossPaymentClient.cancel(payment.getPaymentKey(), new TossCancelRequest("주문 확정 실패 자동 취소"));
        } catch (FeignException e) {
            log.error("보상 취소 실패 — CANCEL_PENDING 잔류: paymentId={}, httpStatus={}",
                    event.paymentId(), e.status());
            return;
        }
        payment.cancel();
        paymentRepository.save(payment);
        log.info("보상 취소 완료: orderId={}, paymentKey={}", payment.getOrderId(), payment.getPaymentKey());
    }
}
