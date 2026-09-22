package com.susuggang.payment;

import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.PaymentRepository;
import com.susuggang.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

// 승인 결과를 알아낸 뒤의 처리 — 사용자 승인 응답·무응답 즉시 조회·잔류 대사 세 경로가 전부 여기로 모인다
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApprovalService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentCompensationService compensationService;

    // 승인 전이는 REQUESTED일 때만(조건부 UPDATE) — 대사 스캔·재요청(더블클릭·새로고침)과 겹쳐도 단일 승자만 주문 확정에 간다.
    // 패배한 쪽이 확정까지 가면 0행을 "확정 불가"로 읽어 정상 결제를 환불한다(장애 주입 실측에서 20/20 오취소) — APPROVED면 그대로 성공
    public void completeApproval(Payment payment, Long buyerId, String approvedAt) {
        int updated = paymentRepository.settleRequested(payment.getId(), PaymentStatus.APPROVED, approvedAt, null);
        if (updated == 0) {
            PaymentStatus current = paymentRepository.findById(payment.getId())
                    .map(Payment::getStatus).orElse(null);
            log.info("승인 전이 경합 패배(이미 처리됨): paymentId={}, orderId={}, status={}",
                    payment.getId(), payment.getOrderId(), current);
            if (current == PaymentStatus.APPROVED) {
                return;
            }
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, Map.of("orderId", payment.getOrderId()));
        }
        confirmOrCompensate(payment, buyerId);
    }

    // 승인 성공 후 주문 확정 — 실패하면 "돈은 나갔는데 주문은 확정 안 됨" → CANCEL_PENDING을
    // 새 트랜잭션으로 남기고(흔적 먼저) 커밋 후 카프카 이벤트로 취소를 위임한다. 유저 응답은 취소를 안 기다린다.
    public void confirmOrCompensate(Payment payment, Long buyerId) {
        try {
            orderService.confirmOrder(buyerId, payment.getOrderId());
        } catch (RuntimeException e) {
            compensationService.requestCancel(payment.getId());
            throw e; // 확정 실패 원인(만료 등)은 그대로 호출자에게
        }
    }
}
