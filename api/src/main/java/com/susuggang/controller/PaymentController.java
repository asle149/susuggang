package com.susuggang.controller;

import com.susuggang.config.LoginMember;
import com.susuggang.dto.CommonResponse;
import com.susuggang.dto.PaymentConfirmRequest;
import com.susuggang.dto.PaymentConfirmResponse;
import com.susuggang.dto.PaymentStatusResponse;
import com.susuggang.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public CommonResponse<PaymentConfirmResponse> confirm(@LoginMember Long memberId,
                                                          @RequestBody PaymentConfirmRequest request) {
        return CommonResponse.success(
                paymentService.confirmPayment(memberId, request.orderId(), request.tossOrderId(),
                        request.paymentKey(), request.amount()));
    }

    // "확인 중" 응답을 받은 클라이언트가 결과를 기다리는 창구 — 재결제 대신 이걸 폴링한다
    @GetMapping("/orders/{orderId}")
    public CommonResponse<PaymentStatusResponse> status(@LoginMember Long memberId,
                                                        @PathVariable Long orderId) {
        return CommonResponse.success(paymentService.getStatus(memberId, orderId));
    }
}
