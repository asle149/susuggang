package com.susuggang.payment;

import com.susuggang.domain.Order;
import com.susuggang.domain.Payment;
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
        PaymentConfirmContext context = new PaymentConfirmContext(orderId, amount, price);
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
            // 토스가 거절 응답을 준 확정 실패만 FAILED — 응답이 없으면(타임아웃 등) 결과를 모르므로 REQUESTED로 남긴다
            if (e.status() >= 400) {
                payment.fail(e.contentUTF8());
                paymentRepository.save(payment);
            }
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, Map.of("orderId", orderId));
        }

        payment.approve(response.approvedAt());
        paymentRepository.save(payment);

        // 승인 성공 후 주문 확정 — 실패하면 "돈은 나갔는데 주문은 확정 안 됨" → CANCEL_PENDING을
        // 새 트랜잭션으로 남기고(흔적 먼저) 커밋 후 카프카 이벤트로 취소를 위임한다. 유저 응답은 취소를 안 기다린다
        try {
            orderService.confirmOrder(buyerId, orderId);
        } catch (RuntimeException e) {
            compensationService.requestCancel(payment.getId());
            throw e; // 확정 실패 원인(만료 등)은 그대로 사용자에게
        }
        log.info("결제 승인·주문 확정: orderId={}, paymentKey={}, amount={}",
                orderId, response.paymentKey(), response.totalAmount());
        return response;
    }
}
