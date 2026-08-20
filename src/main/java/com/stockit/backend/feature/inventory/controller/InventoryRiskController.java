package com.stockit.backend.feature.inventory.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.inventory.dto.response.RiskAssessmentDetailResponse;
import com.stockit.backend.feature.inventory.service.RiskAssessmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "재고 위험 평가", description = "SKU×판매처 단위 서버 위험등급 계산 및 근거 조회 API")
@RestController
@RequestMapping("/api/v1/inventories")
@Validated
public class InventoryRiskController {

    private final RiskAssessmentService riskAssessmentService;

    public InventoryRiskController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @GetMapping("/{skuCode}/sales-points/{salesPointCode}/risk")
    @Operation(
            summary = "선택 재고 위험도 및 근거 조회",
            description = "서버의 결정론적 위험 규칙 엔진을 통해 on_hand_qty 가용재고, 수요예측, 안전재고, 소비기한·판매중지일 기반 위험등급(DANGER, CAUTION, NORMAL, SAFE)을 반환합니다. 판매이력·판매속도·재고보유일수는 이 API에서 사용하지 않습니다."
    )
    public ApiResponse<RiskAssessmentDetailResponse> risk(
            @Parameter(description = "상품 SKU 업무 코드", example = "SKU-4D82A9F1")
            @PathVariable String skuCode,
            @Parameter(description = "판매처 업무 코드", example = "GREETING")
            @PathVariable String salesPointCode
    ) {
        return ApiResponse.of(riskAssessmentService.getRiskAssessment(skuCode, salesPointCode));
    }
}
