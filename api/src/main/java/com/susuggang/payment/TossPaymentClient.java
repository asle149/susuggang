package com.susuggang.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// 인터페이스 선언만으로 구현체는 Feign이 생성 — 인증 헤더 등 공통 처리는 TossFeignConfig의 인터셉터가
@FeignClient(name = "toss-payments", url = "${toss.base-url}", configuration = TossFeignConfig.class)
public interface TossPaymentClient {

    @PostMapping("/v1/payments/confirm")
    TossPaymentResponse confirm(@RequestBody TossConfirmRequest request);

    @GetMapping("/v1/payments/{paymentKey}")
    TossPaymentResponse find(@PathVariable("paymentKey") String paymentKey);

    @PostMapping("/v1/payments/{paymentKey}/cancel")
    TossPaymentResponse cancel(@PathVariable("paymentKey") String paymentKey,
                               @RequestBody TossCancelRequest request);
}
