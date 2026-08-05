package com.susuggang.repository;

import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime before);
}
