package com.susuggang.payment;

import com.susuggang.domain.Order;
import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import com.susuggang.repository.OrderRepository;
import com.susuggang.repository.ProductRepository;
import com.susuggang.service.OrderService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TossPaymentClient tossPaymentClient;
    private final OrderService orderService;

    // 의도적으로 @Transactional 없음 — 외부 호출(토스 승인)이 DB 커넥션·트랜잭션을 물고 기다리지 않게 경계 밖에 둔다
    public TossPaymentResponse confirmPayment(Long buyerId, Long orderId, String tossOrderId,
                                              String paymentKey, Long amount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        int price = productRepository.findById(order.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND))
                .getPrice();

        // 서버가 아는 주문 금액과 대조 — 클라이언트 금액 조작 방지 (토스 문서의 필수 검증)
        if (amount == null || amount != price) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    Map.of("orderId", orderId, "expected", price));
        }

        TossPaymentResponse response;
        try {
            response = tossPaymentClient.confirm(new TossConfirmRequest(paymentKey, tossOrderId, amount));
        } catch (FeignException e) {
            log.warn("토스 승인 실패: orderId={}, httpStatus={}, body={}", orderId, e.status(), e.contentUTF8());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED, Map.of("orderId", orderId));
        }

        // 승인 성공 후 주문 확정 — 여기서 실패하면 "승인은 됐는데 주문은 확정 안 됨" = 보상 처리 지점(P4에서 취소 API로)
        orderService.confirmOrder(buyerId, orderId);
        log.info("결제 승인·주문 확정: orderId={}, paymentKey={}, amount={}",
                orderId, response.paymentKey(), response.totalAmount());
        return response;
    }
}
