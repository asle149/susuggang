package com.susuggang.payment;

import com.susuggang.domain.Payment;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.kafka.PaymentCancelRequestedEvent;
import com.susuggang.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCompensationService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 확정 트랜잭션과 분리된 새 트랜잭션 — 확정이 롤백돼도 "취소할 건이 있다"는 기록은 남아야 한다.
    // PaymentService 안에 두면 자기 호출이 프록시를 안 타 @Transactional이 무시되므로 별도 빈.
    @Transactional
    public void requestCancel(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        payment.cancelPending();
        paymentRepository.save(payment);
        eventPublisher.publishEvent(new PaymentCancelRequestedEvent(payment.getId()));
    }
}
