package com.stockit.backend.feature.statistics.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.statistics.dto.request.StrategyStatisticsDemoBackfillRequest;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsDemoBackfillResponse;
import com.stockit.backend.feature.statistics.service.StrategyStatisticsDemoBackfillService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/statistics/strategies/demo-backfill")
@ConditionalOnProperty(prefix = "app.statistics.strategy-demo-backfill", name = "enabled", havingValue = "true")
public class StrategyStatisticsDemoController {
    private final StrategyStatisticsDemoBackfillService service;

    public StrategyStatisticsDemoController(StrategyStatisticsDemoBackfillService service) {
        this.service = service;
    }

    @Operation(
            summary = "6개월 AI 전략 성과 데모 이력 생성",
            description = "완료 전략, 목표 달성률, 위험재고·폐기위험 감소, 손실 절감과 액션 조합 이력을 생성합니다."
    )
    @PostMapping
    public ApiResponse<StrategyStatisticsDemoBackfillResponse> backfill(
            @RequestBody(required = false) StrategyStatisticsDemoBackfillRequest request
    ) {
        return ApiResponse.of(service.backfill(
                request == null ? null : request.fromDate(),
                request == null ? null : request.toDate()
        ));
    }
}
