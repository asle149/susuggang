package com.susuggang.controller;

import com.susuggang.dto.OrderCreateRequest;
import com.susuggang.service.OptimisticOrderFacade;
import com.susuggang.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OptimisticOrderFacade optimisticOrderFacade;

    // mode는 k6 대조 실험용 스위치 — 실험 후 기본 전략 확정하면서 제거 예정
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Long order(@AuthenticationPrincipal Long memberId,
                      @RequestBody OrderCreateRequest request,
                      @RequestParam(defaultValue = "pessimistic") String mode) {
        return switch (mode) {
            case "conditional" -> orderService.orderWithConditionalUpdate(memberId, request.productId());
            case "optimistic" -> optimisticOrderFacade.order(memberId, request.productId());
            default -> orderService.orderWithLock(memberId, request.productId());
        };
    }
}
