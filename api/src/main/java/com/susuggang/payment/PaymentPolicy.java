package com.susuggang.payment;

public interface PaymentPolicy {
    void check(PaymentConfirmContext context);
}
