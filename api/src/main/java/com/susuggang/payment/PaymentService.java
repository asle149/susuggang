package com.susuggang.payment;

import com.susuggang.domain.Order;
import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.PaymentRepository;
import com.susuggang.repository.ProductRepository;
import com.susuggang.service.OrderService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final OrderService orderService;
    private final PaymentCompensationService compensationService;
    private final List<PaymentPolicy> policyList;
    // 의도적으로 @Transactional 없음 — 외부 호출(토스 승인)이 DB 커넥션·트랜잭션을 물고 기다리지 않게 경계 밖에 둔다
    public TossPaymentResponse confirmPayment(Long buyerId, Long orderId, String tossOrderId,
                                              String paymentKey, Long amount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        int price = productRepository.findById(order.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                .getPrice();

        // 서버가 아는 주문 금액과 대조 — 클라이언트 금액 조작 방지 (토스 문서의 필수 검증)
        PaymentConfirmContext context = new PaymentConfirmContext(orderId, amount, price, buyerId, order.getBuyerId());
        policyList.forEach(p -> p.check(context));

        // 토스 호출 전에 REQUESTED로 기록해야 응답을 못 받아도(타임아웃) 흔적이 남는다.
        // 같은 paymentKey 재요청은 기존 행을 이어 쓴다(unique 제약과 세트).
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseGet(() -> paymentRepository.save(
                        Payment.request(orderId, paymentKey, tossOrderId, price)));

        TossPaymentResponse response;
        try {
            response = tossPaymentClient.confirm(new TossConfirmRequest(paymentKey, tossOrderId, amount));
        } catch (FeignException e) {
            log.warn("토스 승인 실패: orderId={}, httpStatus={}, body={}", orderId, e.status(), e.contentUTF8());
            // 토스가 거절 응답을 준 확정 실패만 FAILED — 응답이 없으면(타임아웃 등) 결과를 모르므로 REQUESTED로 남긴다.
            // 실패 전이도 REQUESTED 가드를 탄다 — 무응답 뒤 같은 키로 재요청하면 토스가 "이미 처리됨" 4xx를 주는데,
            // 그때 대사가 이미 APPROVED로 옮긴 장부를 FAILED로 덮으면 돈은 나갔는데 아무도 다시 보지 않는 건이 된다
            if (e.status() >= 400) {
                int updated = paymentRepository.settleRequested(payment.getId(), PaymentStatus.FAILED, null,
                        Payment.clipFailReason(e.contentUTF8()));
                if (updated == 0) {
                    log.info("실패 전이 생략(이미 처리됨): paymentId={}, orderId={}", payment.getId(), orderId);
                }
            }
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, Map.of("orderId", orderId));
        }

        completeApproval(payment, buyerId, response.approvedAt());
        log.info("결제 승인·주문 확정: orderId={}, paymentKey={}, amount={}",
                orderId, response.paymentKey(), response.totalAmount());
        return response;
    }

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
    // 대사(조회로 DONE 확인) 경로도 이 메서드를 탄다 — 결과를 알아낸 뒤의 처리는 한 곳이어야 한다
    public void confirmOrCompensate(Payment payment, Long buyerId) {
        try {
            orderService.confirmOrder(buyerId, payment.getOrderId());
        } catch (RuntimeException e) {
            compensationService.requestCancel(payment.getId());
            throw e; // 확정 실패 원인(만료 등)은 그대로 호출자에게
        }
    }
}
