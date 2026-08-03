package com.susuggang.payment;

import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentAmountPolicy implements PaymentPolicy {

    @Override
    public void check(PaymentConfirmContext context) {
        if (context.amount() == null || context.amount() != context.price()) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    Map.of("orderId", context.orderId(), "expected", context.price()));
        }
    }
}

