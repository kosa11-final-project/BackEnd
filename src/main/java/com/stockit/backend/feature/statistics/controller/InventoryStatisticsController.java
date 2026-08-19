package com.stockit.backend.feature.statistics.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.request.CreateInventoryStatisticsSnapshotRequest;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.StatisticsSnapshotCreationResponse;
import com.stockit.backend.feature.statistics.service.InventoryStatisticsService;
import com.stockit.backend.feature.statistics.service.StatisticsSnapshotService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/statistics/inventory")
@Tag(name = "재고 통계", description = "동기화 완료 시 저장한 재고 통계 스냅샷을 조회합니다.")
public class InventoryStatisticsController {

    private final InventoryStatisticsService inventoryStatisticsService;
    private final StatisticsSnapshotService statisticsSnapshotService;

    public InventoryStatisticsController(
            InventoryStatisticsService inventoryStatisticsService,
            StatisticsSnapshotService statisticsSnapshotService
    ) {
        this.inventoryStatisticsService = inventoryStatisticsService;
        this.statisticsSnapshotService = statisticsSnapshotService;
    }

    @Operation(
            summary = "재고 통계 조회",
            description = "마지막 정상 통계의 전국·유형별·위치별 지표와 선택 범위의 기간별 위험재고 추이를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "재고 통계 조회 성공"
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "완료된 통계 스냅샷이 없음",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    @GetMapping
    public ApiResponse<InventoryStatisticsResponse> getInventoryStatistics(
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
        return ApiResponse.of(inventoryStatisticsService.getInventoryStatistics(
                fromDate,
                toDate,
                scopeType,
                scopeCode
        ));
    }

    @Operation(
            summary = "재고 통계 스냅샷 생성",
            description = "재고 동기화·수요예측·위험등급 산정 완료 후 호출하는 연계 API입니다. 같은 동기화 작업 ID는 중복 생성하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "재고 통계 스냅샷 생성 또는 기존 결과 반환"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청값이 잘못됨",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "세션이 없거나 만료됨",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    @PostMapping("/snapshots")
    public ApiResponse<StatisticsSnapshotCreationResponse> createInventoryStatisticsSnapshots(
            @Valid @RequestBody CreateInventoryStatisticsSnapshotRequest request
    ) {
        List<Long> snapshotIds = statisticsSnapshotService.createInventorySnapshots(
                request.syncJobId(),
                request.asOfDate()
        );
        return ApiResponse.of(new StatisticsSnapshotCreationResponse(
                request.syncJobId(),
                request.asOfDate(),
                snapshotIds.size(),
                snapshotIds
        ));
    }
}
