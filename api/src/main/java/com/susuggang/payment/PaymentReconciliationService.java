package com.susuggang.payment;

import com.susuggang.domain.Order;
import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.exception.BusinessException;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.PaymentRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentService paymentService;
    private final PaymentCompensationService compensationService;

    public enum Result {
        APPROVED, FAILED, CANCELED, UNRESOLVED
    }

    // @Transactional 없음 — 외부 조회가 커넥션·트랜잭션을 물지 않게 경계 밖(confirmPayment와 같은 원칙).
    // 전이는 REQUESTED 가드가 있는 조건부 UPDATE라 스캔~전이 사이의 사용자 재요청과 겹쳐도 단일 승자
    public Result reconcile(Payment payment) {
        TossPaymentResponse response;
        try {
            response = tossPaymentClient.find(payment.getPaymentKey());
        } catch (FeignException e) {
            log.warn("결제 조회 실패: paymentId={}, orderId={}, httpStatus={}",
                    payment.getId(), payment.getOrderId(), e.status());
            return Result.UNRESOLVED;
        }

        if (response == null) {
            log.error("결제 조회 응답 없음: paymentId={}, orderId={}",
                    payment.getId(), payment.getOrderId());
            return Result.UNRESOLVED;
        }
        if (!hasMatchingIdentity(payment, response)) {
            return Result.UNRESOLVED;
        }

        String status = response.status();
        if ("DONE".equals(status)) {
            if (!Objects.equals(response.totalAmount(), (long) payment.getAmount())) {
                log.error("결제 조회 응답 불일치: paymentId={}, orderId={}, expectedAmount={}, actualAmount={}",
                        payment.getId(), payment.getOrderId(), payment.getAmount(), response.totalAmount());
                return Result.UNRESOLVED;
            }
            return reconcileApproved(payment, response.approvedAt());
        }
        if ("PARTIAL_CANCELED".equals(status)) {
            log.error("부분 취소 상태, 수동 확인 필요: paymentId={}, orderId={}",
                    payment.getId(), payment.getOrderId());
            return Result.UNRESOLVED;
        }
        if ("CANCELED".equals(status)) {
            return settle(payment, PaymentStatus.CANCELED, null, null)
                    ? Result.CANCELED : Result.UNRESOLVED;
        }
        if ("ABORTED".equals(status) || "EXPIRED".equals(status)) {
            return settle(payment, PaymentStatus.FAILED, null, "toss status=" + status)
                    ? Result.FAILED : Result.UNRESOLVED;
        }

        log.warn("미확정 결제 잔류: paymentId={}, orderId={}, tossStatus={}",
                payment.getId(), payment.getOrderId(), status);
        return Result.UNRESOLVED;
    }

    private boolean hasMatchingIdentity(Payment payment, TossPaymentResponse response) {
        if (Objects.equals(response.paymentKey(), payment.getPaymentKey())
                && Objects.equals(response.orderId(), payment.getTossOrderId())) {
            return true;
        }

        log.error("결제 조회 응답 불일치: paymentId={}, expectedPaymentKey={}, actualPaymentKey={}, " +
                        "expectedOrderId={}, actualOrderId={}",
                payment.getId(), payment.getPaymentKey(), response.paymentKey(),
                payment.getTossOrderId(), response.orderId());
        return false;
    }

    private Result reconcileApproved(Payment payment, String approvedAt) {
        if (!settle(payment, PaymentStatus.APPROVED, approvedAt, null)) {
            return Result.UNRESOLVED;
        }

        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order != null) {
            try {
                paymentService.confirmOrCompensate(payment, order.getBuyerId());
            } catch (BusinessException e) {
                log.warn("승인 확인, 주문 확정 실패 → 보상 경로: paymentId={}, orderId={}, errorCode={}",
                        payment.getId(), payment.getOrderId(), e.getErrorCode());
            }
            return Result.APPROVED;
        }

        log.error("승인 확인, 주문 없음 → 보상 경로: paymentId={}, orderId={}",
                payment.getId(), payment.getOrderId());
        compensationService.requestCancel(payment.getId());
        return Result.APPROVED;
    }

    private boolean settle(Payment payment, PaymentStatus status, String approvedAt, String failReason) {
        int updated = paymentRepository.settleRequested(
                payment.getId(), status, approvedAt, failReason);
        if (updated == 0) {
            log.info("대사 경합 패배(이미 처리됨): paymentId={}, orderId={}, targetStatus={}",
                    payment.getId(), payment.getOrderId(), status);
            return false;
        }
        return true;
    }
}
