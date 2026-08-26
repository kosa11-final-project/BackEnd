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
        @Schema(description = "액션 기간과 조회 기준일로 계산한 실행 진행률. 완료 전략은 100")
        Integer progress,
        @Schema(description = "선택 전략의 추천 근거", nullable = true)
        String goal,
        @Schema(description = "strategy_execution_result의 목표·실제 판매량 기반 결과 요약", nullable = true)
        String resultSummary,
        List<Action> actions,
        List<InventoryResult> inventoryResults,
        @Schema(description = "재고 이동 액션의 출발·도착 거점별 이동 수량")
        List<InventoryTransfer> inventoryTransfers,
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

    @Schema(description = "출발·도착 거점별로 집계한 재고 이동 내역")
    public record InventoryTransfer(
            @Schema(description = "재고 이동 액션 유형", example = "RT_TRANSFER") String actionType,
            @Schema(description = "출발 거점 ID", example = "501") Long fromLocationId,
            @Schema(description = "출발 거점명", example = "성남 물류센터") String fromLocationName,
            @Schema(description = "도착 센터가 있으면 센터, 없으면 대상 판매처의 ID", example = "502")
            Long toLocationId,
            @Schema(description = "도착 센터가 있으면 센터, 없으면 대상 판매처의 이름", example = "경인1센터")
            String toLocationName,
            @Schema(description = "출발 센터 ID", example = "501", nullable = true)
            Long sourceWarehouseId,
            @Schema(description = "출발 센터명", example = "성남센터", nullable = true)
            String sourceWarehouseName,
            @Schema(description = "출발 판매처 ID", example = "9", nullable = true)
            Long sourceSalesPointId,
            @Schema(description = "출발 판매처명", example = "현대백화점 무역센터점", nullable = true)
            String sourceSalesPointName,
            @Schema(description = "실제 재고 도착 센터 ID", example = "502", nullable = true)
            Long destinationWarehouseId,
            @Schema(description = "실제 재고 도착 센터명", example = "경인1센터", nullable = true)
            String destinationWarehouseName,
            @Schema(description = "재고 이동의 대상 판매처 ID", example = "10", nullable = true)
            Long targetSalesPointId,
            @Schema(description = "재고 이동의 대상 판매처명", example = "그리팅", nullable = true)
            String targetSalesPointName,
            @Schema(description = "이동 수량. 항상 양수", example = "121") BigDecimal quantity
    ) {
        public InventoryTransfer(
                Long fromLocationId, String fromLocationName, Long toLocationId, String toLocationName,
                Long destinationWarehouseId, String destinationWarehouseName,
                Long targetSalesPointId, String targetSalesPointName, BigDecimal quantity
        ) {
            this(null, fromLocationId, fromLocationName, toLocationId, toLocationName,
                    null, null, null, null, destinationWarehouseId, destinationWarehouseName,
                    targetSalesPointId, targetSalesPointName, quantity);
        }
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
            BigDecimal actualRemainingQuantity
    ) {
    }
}
