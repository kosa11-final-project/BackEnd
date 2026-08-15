package com.stockit.backend.feature.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.dashboard.dto.response.DashboardResponse;
import com.stockit.backend.feature.dashboard.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "대시보드", description = "전국 재고 현황과 우선 처리 대상을 조회합니다.")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(
            summary = "재고 운영 대시보드 조회",
            description = "전국 핵심 지표, 물류센터와 오프라인 매장 재고, 위험 판매처 TOP 10, 긴급 SKU TOP 5를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대시보드 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "세션이 없거나 만료됨",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "그린푸드 총괄 권한이 없음",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard() {
        return ApiResponse.of(dashboardService.getDashboard());
    }
}
