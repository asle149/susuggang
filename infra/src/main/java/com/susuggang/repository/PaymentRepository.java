package com.susuggang.repository;

import com.susuggang.domain.Payment;
import com.susuggang.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime before);

    // 스캔 이후 정상 요청이 끝났으면 오래된 객체가 완료 상태를 덮어쓰지 못하도록 REQUESTED만 전이한다.
    // 외부 조회와 분리해 조건부 갱신에 필요한 트랜잭션만 짧게 연다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Payment p set p.status = :status, p.approvedAt = :approvedAt, p.failReason = :failReason " +
            "where p.id = :id and p.status = com.susuggang.domain.PaymentStatus.REQUESTED")
    int settleRequested(@Param("id") Long id, @Param("status") PaymentStatus status,
                        @Param("approvedAt") String approvedAt, @Param("failReason") String failReason);
}
