package com.susuggang.repository;

import com.susuggang.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findBySellerIdOrderBySettledAtDesc(Long sellerId);
}
