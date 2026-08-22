package com.stockit.backend.feature.statistics.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.statistics.dto.request.InventoryStatisticsDemoBackfillRequest;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsDemoBackfillResponse;
import com.stockit.backend.feature.statistics.service.InventoryStatisticsDemoBackfillService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/statistics/inventory/demo-backfill")
@ConditionalOnProperty(prefix = "app.statistics.demo-backfill", name = "enabled", havingValue = "true")
public class InventoryStatisticsDemoController {
    private final InventoryStatisticsDemoBackfillService service;

    public InventoryStatisticsDemoController(InventoryStatisticsDemoBackfillService service) {
        this.service = service;
    }

    @Operation(
            summary = "6개월 재고 통계 데모 이력 생성",
            description = "현재 실제 집계와 실제 일별 판매 흐름을 기준으로 재현 가능한 과거 통계 스냅샷을 생성합니다."
    )
    @PostMapping
    public ApiResponse<InventoryStatisticsDemoBackfillResponse> backfill(
            @RequestBody(required = false) InventoryStatisticsDemoBackfillRequest request
    ) {
        return ApiResponse.of(service.backfill(
                request == null ? null : request.fromDate(),
                request == null ? null : request.toDate()
        ));
    }
}
