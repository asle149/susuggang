package com.susuggang.payment;

import com.susuggang.exception.BusinessException;
import com.susuggang.exception.ErrorCode;
import org.springframework.stereotype.Component;


@Component
public class OrderOwnerPolicy implements PaymentPolicy {

    @Override
    public void check(PaymentConfirmContext context) {
        if (!context.orderBuyerId().equals(context.buyerId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
