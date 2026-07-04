package com.susuggang.repository;

import com.susuggang.domain.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId); //읽기용

    //비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.productId = :productId")
    Optional<Stock> findByProductIdForUpdate(@Param("productId") Long productId); //차감용

    //조건부 UPDATE
    @Modifying(clearAutomatically = true)
    @Query("update Stock s set s.quantity = s.quantity - 1 " + "where s.productId = :productId and s.quantity >= 1")
    int decreaseStock(@Param("productId") Long productID);

    //예약 만료 복구: 전이 성공 건수만큼만 호출되므로 총량 초과 없음
    @Modifying(clearAutomatically = true)
    @Query("update Stock s set s.quantity = s.quantity + 1 where s.productId = :productId")
    int increaseStock(@Param("productId") Long productId);
}
