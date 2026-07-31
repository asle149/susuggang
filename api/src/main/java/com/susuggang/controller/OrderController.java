package com.susuggang.controller;

import com.susuggang.dto.CommonResponse;
import com.susuggang.dto.OrderCreateRequest;
import com.susuggang.dto.OrderCreateResponse;
import com.susuggang.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import com.susuggang.config.LoginMember;
import org.springframework.web.bind.annotation.PathVariable;
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
    public CommonResponse<OrderCreateResponse> order(@LoginMember Long memberId,
                                                     @RequestBody OrderCreateRequest request){
        return CommonResponse.success(
                orderService.orderWithConditionalUpdate(memberId, request.productId())
        );
    }

    @PostMapping("/{orderId}/confirm")
    public CommonResponse<Void> confirm(@LoginMember Long memberId,
                        @PathVariable Long orderId) {
        orderService.confirmOrder(memberId, orderId);
        return CommonResponse.ok();
    }
}
