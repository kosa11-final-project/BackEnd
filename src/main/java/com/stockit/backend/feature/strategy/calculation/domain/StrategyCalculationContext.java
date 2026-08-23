package com.stockit.backend.feature.strategy.calculation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 하나의 AI 전략 Case를 동일한 입력으로 반복 계산하기 위한 불변 스냅샷.
 *
 * <p>계산 엔진이 DB와 Redis를 직접 조회하지 않도록 요청, 재고, 가격, 원가,
 * 정책, 일별 수요예측을 계산 경계에서 한 번에 조립한다.</p>
 */
public record StrategyCalculationContext(
        Long strategyCaseId,
        Long sourceSalesPointId,
        LocalDateTime calculatedAt,
        LocalDate forecastStartDate,
        LocalDate forecastEndDate,
        Sku sku,
        BigDecimal unitCost,
        RequestConstraints requestConstraints,
        List<InventoryLot> evaluationInventory,
        List<InventoryLot> referenceInventory,
        List<InventoryPolicy> inventoryPolicies,
        Map<Long, SalesPoint> salesPoints,
        ForecastMetadata forecastMetadata
) {

    public StrategyCalculationContext {
        if (strategyCaseId == null || strategyCaseId <= 0) {
            throw new IllegalArgumentException("strategyCaseId must be positive");
        }
        if (calculatedAt == null
                || forecastStartDate == null
                || forecastEndDate == null
                || forecastStartDate.isAfter(forecastEndDate)
                || sku == null
                || unitCost == null
                || unitCost.signum() < 0
                || requestConstraints == null
                || forecastMetadata == null) {
            throw new IllegalArgumentException("calculation context metadata is invalid");
        }
        evaluationInventory = List.copyOf(evaluationInventory);
        referenceInventory = List.copyOf(referenceInventory);
        inventoryPolicies = List.copyOf(inventoryPolicies);
        salesPoints = Collections.unmodifiableMap(new LinkedHashMap<>(salesPoints));
        if (evaluationInventory.isEmpty()) {
            throw new IllegalArgumentException("evaluation inventory must not be empty");
        }
    }

    /** 사용자 고정 시작일이 있으면 우선하고, 없으면 예측 시작일을 사용한다. */
    public LocalDate strategyStartDate() {
        return requestConstraints.preferredStartDate() != null
                ? requestConstraints.preferredStartDate()
                : forecastStartDate;
    }

    /** 사용자 고정 종료일이 있으면 우선하고, 없으면 예측 종료일을 사용한다. */
    public LocalDate strategyEndDate() {
        return requestConstraints.preferredEndDate() != null
                ? requestConstraints.preferredEndDate()
                : forecastEndDate;
    }

    /** 사용자 선택 순서와 직접 입력한 기간을 후속 후보·AI 단계까지 보존한다. */
    public record RequestConstraints(
            List<Long> orderedCandidateSalesPointIds,
            List<StrategyType> orderedStrategyTypes,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate
    ) {
        public RequestConstraints {
            orderedCandidateSalesPointIds = List.copyOf(orderedCandidateSalesPointIds);
            orderedStrategyTypes = List.copyOf(orderedStrategyTypes);
        }

        public boolean isStartDateFixed() {
            return preferredStartDate != null;
        }

        public boolean isEndDateFixed() {
            return preferredEndDate != null;
        }
    }

    public record Sku(
            Long skuId,
            String skuCode,
            String skuName,
            String unitCode,
            BigDecimal packageQuantity
    ) {
        public Sku {
            if (skuId == null || skuId <= 0
                    || skuCode == null || skuCode.isBlank()
                    || skuName == null || skuName.isBlank()
                    || unitCode == null || unitCode.isBlank()
                    || packageQuantity == null || packageQuantity.signum() <= 0) {
                throw new IllegalArgumentException("sku calculation input is invalid");
            }
        }
    }

    /**
     * inventory_balance 한 행에 대응하는 계산 대상 재고.
     * availableQty는 프로젝트 정의상 on_hand_qty이며 reservedQty를 다시 차감하지 않는다.
     */
    public record InventoryLot(
            Long inventoryBalanceId,
            Long lotId,
            Long warehouseId,
            Long stockSalesPointId,
            Long allocatedSalesPointId,
            BigDecimal availableQty,
            BigDecimal reservedQty,
            LocalDate manufacturedDate,
            LocalDate receivedDate,
            LocalDate expiryDate,
            LocalDate saleStopDate,
            String lotStatus
    ) {
        public InventoryLot {
            if (inventoryBalanceId == null || inventoryBalanceId <= 0
                    || availableQty == null || availableQty.signum() < 0
                    || reservedQty == null || reservedQty.signum() < 0) {
                throw new IllegalArgumentException("inventory lot input is invalid");
            }
        }

        public Long effectiveSalesPointId() {
            return stockSalesPointId != null
                    ? stockSalesPointId
                    : allocatedSalesPointId;
        }

        public boolean isPublicUnassigned() {
            return stockSalesPointId == null && allocatedSalesPointId == null;
        }
    }

    public record Price(
            Long skuChannelPriceId,
            BigDecimal sellingPrice,
            BigDecimal actualPrice,
            BigDecimal minimumSellingPrice,
            BigDecimal paymentFee,
            BigDecimal logisticsCost
    ) {
        public Price {
            if (skuChannelPriceId == null || skuChannelPriceId <= 0
                    || sellingPrice == null || sellingPrice.signum() < 0
                    || actualPrice == null || actualPrice.signum() < 0
                    || paymentFee == null || paymentFee.signum() < 0
                    || logisticsCost == null || logisticsCost.signum() < 0) {
                throw new IllegalArgumentException("price calculation input is invalid");
            }
        }
    }

    /** 후보 판매처의 기존 재고는 평가 대상이 아니라 이동 가능성 계산용 참고값이다. */
    public record SalesPoint(
            Long salesPointId,
            String salesPointCode,
            String salesPointName,
            BigDecimal existingAvailableQty,
            boolean currentlyListed,
            Price price,
            Map<LocalDate, BigDecimal> dailyForecast,
            List<WarehouseRoute> warehouseRoutes
    ) {
        public SalesPoint(
                Long salesPointId,
                String salesPointCode,
                String salesPointName,
                BigDecimal existingAvailableQty,
                Price price,
                Map<LocalDate, BigDecimal> dailyForecast,
                List<WarehouseRoute> warehouseRoutes
        ) {
            this(
                    salesPointId,
                    salesPointCode,
                    salesPointName,
                    existingAvailableQty,
                    price != null,
                    price,
                    dailyForecast,
                    warehouseRoutes
            );
        }

        public SalesPoint {
            if (salesPointId == null || salesPointId <= 0
                    || salesPointCode == null || salesPointCode.isBlank()
                    || salesPointName == null || salesPointName.isBlank()
                    || existingAvailableQty == null
                    || existingAvailableQty.signum() < 0) {
                throw new IllegalArgumentException("sales point calculation input is invalid");
            }
            dailyForecast = Collections.unmodifiableMap(
                    new LinkedHashMap<>(dailyForecast)
            );
            warehouseRoutes = warehouseRoutes.stream()
                    .sorted(Comparator
                            .comparing(
                                    WarehouseRoute::priorityNo,
                                    Comparator.nullsLast(Comparator.naturalOrder())
                            )
                            .thenComparing(WarehouseRoute::salesPointWarehouseId))
                    .toList();
        }

        public boolean hasCompletePrice() {
            return price != null;
        }
    }

    public record WarehouseRoute(
            Long salesPointWarehouseId,
            Long salesPointId,
            Long warehouseId,
            Integer priorityNo,
            BigDecimal baseDeliveryCost
    ) {
        public WarehouseRoute {
            if (salesPointWarehouseId == null || salesPointWarehouseId <= 0
                    || salesPointId == null || salesPointId <= 0
                    || warehouseId == null || warehouseId <= 0
                    || (baseDeliveryCost != null && baseDeliveryCost.signum() < 0)) {
                throw new IllegalArgumentException("warehouse route input is invalid");
            }
        }
    }

    public record InventoryPolicy(
            Long inventoryPolicyId,
            Long warehouseId,
            Long stockSalesPointId,
            Long allocatedSalesPointId,
            BigDecimal safetyStockQty,
            BigDecimal targetStockQty,
            BigDecimal dailyUnitHoldingCost,
            BigDecimal unitDisposalCost
    ) {
        public InventoryPolicy {
            if (inventoryPolicyId == null || inventoryPolicyId <= 0
                    || safetyStockQty == null || safetyStockQty.signum() < 0) {
                throw new IllegalArgumentException("inventory policy input is invalid");
            }
        }
    }

    public record ForecastMetadata(
            String forecastRunId,
            Long modelVersionId,
            OffsetDateTime forecastGeneratedAt
    ) {
        public ForecastMetadata {
            if (forecastRunId == null || forecastRunId.isBlank()
                    || modelVersionId == null || modelVersionId <= 0
                    || forecastGeneratedAt == null) {
                throw new IllegalArgumentException("forecast metadata is invalid");
            }
        }
    }
}
