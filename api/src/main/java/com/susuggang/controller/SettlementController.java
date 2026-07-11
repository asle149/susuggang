package com.susuggang.controller;

import com.susuggang.dto.CommonResponse;
import com.susuggang.dto.SettlementResponse;
import com.susuggang.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public CommonResponse<List<SettlementResponse>> my(@AuthenticationPrincipal Long memberId){
        return CommonResponse.success(
                settlementService.findMine(memberId)
        );
    }
}
