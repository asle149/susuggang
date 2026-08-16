package com.susuggang.scheduler;

import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.kafka.PaymentCancelRequestedEvent;
import com.susuggang.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRemnantScheduler {

    // 정상 흐름이면 초 단위로 끝나는 상태들 — 10분을 넘겼다면 이벤트 유실·DLQ행까지 전부 실패한 잔류다
    private static final Duration REMNANT_THRESHOLD = Duration.ofMinutes(10);

    // 재투입 상한 — 여기까지 실패했으면 일시 장애가 아니라고 보고 자동 복구를 끊는다 (무한 재투입 방지)
    private static final int CANCEL_RETRY_CAP = 3;

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.compensation-topic:payment-compensation}")
    private String compensationTopic;

    // initialDelay: 컨텍스트가 뜰 때마다 도는 소음 방지 — 재기동 직후 잔류도 다음 주기에 잡힌다
    @Scheduled(initialDelay = 600_000, fixedDelay = 600_000)
    public void scanRemnants() {
        scanRemnants(LocalDateTime.now());
    }

    public void scanRemnants(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(REMNANT_THRESHOLD);

        // 취소해야 하는데 아직 못 한 건 — 보상 토픽에 재투입. 실행은 컨슈머 단일 경로로 유지해야
        // 멱등 가드도 한 곳이면 된다 (스케줄러는 발견과 재투입만)
        List<Payment> stalePending = paymentRepository.findByStatusAndCreatedAtBefore(
                PaymentStatus.CANCEL_PENDING, cutoff);
        int reinjected = 0;
        for (Payment payment : stalePending) {
            if (payment.getCancelRetryCount() >= CANCEL_RETRY_CAP) {
                payment.failCancel();
                paymentRepository.save(payment);
                // 알림 채널 연동 전까지는 error 로그가 알림 자리 (키바나 발견용)
                log.error("보상 취소 상한 초과, 수동 처리 필요: paymentId={}, orderId={}, retryCount={}",
                        payment.getId(), payment.getOrderId(), payment.getCancelRetryCount());
                continue;
            }
            // 발행보다 증가가 먼저 — 발행이 실패하면 횟수만 오르고 다음 주기가 흡수하지만,
            // 반대 순서는 카운트 없는 재투입이라 상한이 뚫린다
            payment.increaseCancelRetry();
            paymentRepository.save(payment);
            kafkaTemplate.send(compensationTopic, String.valueOf(payment.getId()),
                    new PaymentCancelRequestedEvent(payment.getId()));
            reinjected++;
        }
        if (reinjected > 0) {
            log.warn("보상 잔류 재투입: {}건", reinjected);
        }

        // 결과를 모르는 건(무응답 잔류)은 자동 판정하지 않는다 — 재시도는 이중 승인 위험이라는
        // 무재시도 원칙 그대로, 발견·기록까지만 하고 처리는 사람이 판단한다
        List<Payment> staleRequested = paymentRepository.findByStatusAndCreatedAtBefore(
                PaymentStatus.REQUESTED, cutoff);
        for (Payment payment : staleRequested) {
            log.warn("미확정 결제 잔류: paymentId={}, orderId={}, createdAt={}",
                    payment.getId(), payment.getOrderId(), payment.getCreatedAt());
        }
    }
}
