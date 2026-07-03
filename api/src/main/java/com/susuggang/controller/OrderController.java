package com.susuggang.controller;

import com.susuggang.dto.OrderCreateRequest;
import com.susuggang.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Long order(@AuthenticationPrincipal Long memberId,
                      @RequestBody OrderCreateRequest request) {
        return orderService.orderWithConditionalUpdate(memberId, request.productId()); //조건부 UPDATE 확정
    }
}
