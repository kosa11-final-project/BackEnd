package com.stockit.backend.feature.strategy.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최종 선택된 AI 전략의 실행 관제 정보")
public record StrategyExecutionResponse(
        @Schema(description = "외부 상세 식별자로 사용하는 전략 케이스 ID", example = "101")
        Long id,
        @Schema(description = "전략 업무 번호", example = "SC-20260820-001")
        String number,
        @Schema(description = "전략 실행 상태. 현재 케이스 상태로 확정할 수 없으면 null", nullable = true)
        String status,
        Product product,
        @Schema(description = "최종 전략 선택일")
        LocalDate establishedAt,
        @Schema(description = "실행 진행률. 현 스키마에서는 산출할 수 없어 null", nullable = true)
        Integer progress,
        @Schema(description = "선택 전략의 추천 근거", nullable = true)
        String goal,
        @Schema(description = "실행 결과 요약. 현 스키마에서는 산출할 수 없어 null", nullable = true)
        String resultSummary,
        List<Action> actions,
        List<InventoryResult> inventoryResults,
        List<ChannelResult> channelResults,
        List<DailySales> salesDaily,
        List<SalesPointComparison> salesPointComparison,
        Performance performance,
        @Schema(description = "최근 성과 동기화 완료 시각", nullable = true)
        Instant lastSyncedAt
) {
    public record Product(Long skuId, String name, String sku, String imageUrl) {
    }

    public record Location(Long id, String code, String name, String type) {
    }

    public record Kpi(
            String label,
            Object value,
            String unit,
            boolean representative,
            String emptyLabel
    ) {
    }

    public record Action(
            Long id,
            String type,
            String title,
            String target,
            String relationship,
            List<Long> dependsOn,
            String status,
            Integer progress,
            String note,
            BigDecimal actionQuantity,
            LocalDate startDate,
            LocalDate endDate,
            Location sourceSalesPoint,
            Location targetSalesPoint,
            Location sourceWarehouse,
            Location destinationWarehouse,
            List<Kpi> kpis
    ) {
    }

    public record InventoryResult(
            String location,
            String locationType,
            Long locationId,
            String locationCode,
            BigDecimal before,
            BigDecimal moved,
            BigDecimal after,
            BigDecimal safetyStockQuantity,
            String guardrail
    ) {
    }

    public record ChannelResult(
            Long salesPointId,
            String channel,
            String status,
            BigDecimal sales,
            BigDecimal revenue,
            String cannibalization
    ) {
    }

    public record DailySales(
            LocalDate date,
            Long salesPointId,
            String salesPointCode,
            String salesPoint,
            BigDecimal quantity,
            BigDecimal revenue
    ) {
    }

    public record SalesPointComparison(
            Long salesPointId,
            String salesPointCode,
            String salesPoint,
            String role,
            String label
    ) {
    }

    public record Performance(
            BigDecimal actualSalesQuantity,
            BigDecimal actualRevenue,
            BigDecimal actualContributionMargin,
            BigDecimal actualRemainingQuantity,
            BigDecimal movedQuantity,
            BigDecimal disposedQuantity
    ) {
    }
}
