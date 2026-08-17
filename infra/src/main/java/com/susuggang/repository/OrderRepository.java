package com.susuggang.repository;

import com.susuggang.domain.Order;
import com.susuggang.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime now);

    // 상태 전이도 조건부 UPDATE: RESERVED일 때만 성공(영향 행 수로 판정), 만료분은 거부
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = com.susuggang.domain.OrderStatus.COMPLETED " +
            "where o.id = :orderId and o.buyerId = :buyerId " +
            "and o.status = com.susuggang.domain.OrderStatus.RESERVED and o.expiresAt > :now")
    int confirmReserved(@Param("orderId") Long orderId, @Param("buyerId") Long buyerId, @Param("now") LocalDateTime now);

    // 스캔~전이 사이에 confirm이 끼어들어도 status 가드가 단일 승자 보장
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = com.susuggang.domain.OrderStatus.CANCELED " +
            "where o.id = :orderId and o.status = com.susuggang.domain.OrderStatus.RESERVED")
    int cancelReserved(@Param("orderId") Long orderId);

    // 확정됐는데 정산 장부가 없는 잔류 — 확정 시각 컬럼이 없어 expiresAt(예약 마감)이 cutoff를
    // 지난 건만 잡는다: 확정은 마감 전에만 가능하므로 "확정 후 cutoff 이상 경과"가 보장된다
    @Query("select o from Order o where o.status = com.susuggang.domain.OrderStatus.COMPLETED " +
            "and o.expiresAt < :cutoff " +
            "and not exists (select 1 from Settlement s where s.orderId = o.id)")
    List<Order> findSettlementRemnants(@Param("cutoff") LocalDateTime cutoff);
}
