package com.susuggang.payment;

import com.susuggang.domain.Order;
import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import com.susuggang.dto.PaymentConfirmResponse;
import com.susuggang.dto.PaymentStatusResponse;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.payment.PaymentReconciliationService.Inquiry;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.PaymentRepository;
import com.susuggang.repository.ProductRepository;
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
    private final PaymentApprovalService approvalService;
    private final PaymentReconciliationService reconciliationService;
    private final List<PaymentPolicy> policyList;

    // 의도적으로 @Transactional 없음 — 외부 호출(토스 승인)이 DB 커넥션·트랜잭션을 물고 기다리지 않게 경계 밖에 둔다
    public PaymentConfirmResponse confirmPayment(Long buyerId, Long orderId, String tossOrderId,
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
                throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, Map.of("orderId", orderId));
            }
            return resolveUnanswered(payment, buyerId);
        }

        approvalService.completeApproval(payment, buyerId, response.approvedAt());
        log.info("결제 승인·주문 확정: orderId={}, paymentKey={}, amount={}",
                orderId, response.paymentKey(), response.totalAmount());
        return PaymentConfirmResponse.approved(orderId, response.paymentKey(), response.approvedAt());
    }

    // 응답이 없으면 승인을 다시 보내지 않고(이중 승인 위험) 그 자리에서 조회로 한 번 묻는다.
    // 조회로도 모르면 실패로 단정하지 않고 "확인 중"을 돌려주며, 이후는 잔류 스캔이 이어받는다
    private PaymentConfirmResponse resolveUnanswered(Payment payment, Long buyerId) {
        Inquiry inquiry = reconciliationService.inquire(payment);
        switch (inquiry.result()) {
            case APPROVED -> {
                approvalService.confirmOrCompensate(payment, buyerId);
                log.info("승인 무응답 → 조회로 승인 확인·주문 확정: paymentId={}, orderId={}",
                        payment.getId(), payment.getOrderId());
                return PaymentConfirmResponse.approved(payment.getOrderId(), payment.getPaymentKey(),
                        inquiry.response().approvedAt());
            }
            case FAILED, CANCELED -> throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED,
                    Map.of("orderId", payment.getOrderId()));
            default -> {
                log.warn("승인 무응답, 조회로도 미확정 → 확인 중 응답: paymentId={}, orderId={}",
                        payment.getId(), payment.getOrderId());
                return PaymentConfirmResponse.pending(payment.getOrderId(), payment.getPaymentKey());
            }
        }
    }

    public PaymentStatusResponse getStatus(Long buyerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getBuyerId().equals(buyerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return paymentRepository.findTopByOrderIdOrderByIdDesc(orderId)
                .map(p -> PaymentStatusResponse.of(orderId, p.getStatus(), order.getStatus()))
                .orElse(PaymentStatusResponse.none(orderId));
    }
}
