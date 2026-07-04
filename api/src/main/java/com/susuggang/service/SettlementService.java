package com.susuggang.service;

import com.susuggang.dto.SettlementResponse;
import com.susuggang.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;

    // 정산은 판매자 본인 것만 — 로그인 사용자를 sellerId로 조회
    @Transactional(readOnly = true)
    public List<SettlementResponse> findMine(Long sellerId) {
        return settlementRepository.findBySellerIdOrderBySettledAtDesc(sellerId).stream()
                .map(s -> new SettlementResponse(s.getOrderId(), s.getProductId(), s.getAmount(), s.getSettledAt()))
                .toList();
    }
}
