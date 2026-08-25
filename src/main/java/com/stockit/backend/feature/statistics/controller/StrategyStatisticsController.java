package com.stockit.backend.feature.statistics.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsResponse;
import com.stockit.backend.feature.statistics.service.StrategyStatisticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/statistics/strategies")
@Tag(name = "AI 전략 통계", description = "실행이 종료되어 최종 성과가 확정된 AI 전략을 집계합니다.")
public class StrategyStatisticsController {

    private final StrategyStatisticsService strategyStatisticsService;

    public StrategyStatisticsController(StrategyStatisticsService strategyStatisticsService) {
        this.strategyStatisticsService = strategyStatisticsService;
    }

    @Operation(
            summary = "AI 전략 성과 통계 조회",
            description = "조회 기간에 실행이 종료된 전략의 핵심 성과, 일별 추이, 실제 액션 조합별 성과를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "AI 전략 통계 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "날짜 범위 또는 통계 범위가 잘못됨",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "세션이 없거나 만료됨",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    @GetMapping
    public ApiResponse<StrategyStatisticsResponse> getStrategyStatistics(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,
            @RequestParam(required = false, defaultValue = "NATIONAL")
            StatisticsScopeType scopeType,
            @RequestParam(required = false)
            String scopeCode
    ) {
        return ApiResponse.of(strategyStatisticsService.getStrategyStatistics(
                fromDate,
                toDate,
                scopeType,
                scopeCode
        ));
    }
}
