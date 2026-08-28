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
            description = "최근 재고 동기화 때 저장한 SKU×판매처 위험등급·대표 사유·판정시각을 반환합니다. 가용재고와 30일 예상 폐기수량·폐기율 등 별도 저장하지 않는 표시용 파생값은 현재 통합재고·LOT·최신 수요예측으로 계산합니다. 판매처 코드는 UNASSIGNED를 지원합니다."
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
