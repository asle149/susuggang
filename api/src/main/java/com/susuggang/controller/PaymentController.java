package com.susuggang.controller;

import com.susuggang.dto.CommonResponse;
import com.susuggang.dto.PaymentConfirmRequest;
import com.susuggang.payment.PaymentService;
import com.susuggang.payment.TossPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    public CommonResponse<TossPaymentResponse> confirm(@AuthenticationPrincipal Long memberId,
                                                       @RequestBody PaymentConfirmRequest request) {
        return CommonResponse.success(
                paymentService.confirmPayment(memberId, request.orderId(), request.tossOrderId(),
                        request.paymentKey(), request.amount()));
    }
}
