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
            description = "가장 최근에 정상 완료된 동기화의 대시보드 집계 결과를 반환합니다."
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "완료된 대시보드 스냅샷이 없음",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard() {
        return ApiResponse.of(dashboardService.getDashboard());
    }

    @Operation(
            summary = "재고 운영 대시보드 실시간 집계 조회",
            description = "원본 재고·수요예측·위험평가 테이블을 즉시 집계하는 검증용 API입니다. 응답이 오래 걸릴 수 있으며 운영 화면에서는 사용하지 않습니다.",
            deprecated = true
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "실시간 대시보드 집계 성공"
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
    @GetMapping("/live")
    public ApiResponse<DashboardResponse> getLiveDashboard() {
        return ApiResponse.of(dashboardService.getLiveDashboard());
    }
}
