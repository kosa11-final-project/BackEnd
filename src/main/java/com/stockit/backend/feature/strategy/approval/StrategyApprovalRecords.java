package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.stockit.backend.common.persistence.BaseEntity;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;

import lombok.Getter;
import lombok.Setter;

/** 최종 전략 영속화 Mapper의 읽기·쓰기 모델 모음. */
public final class StrategyApprovalRecords {

    private StrategyApprovalRecords() {
    }

    @Getter
    @Setter
    public static class CaseRecord {
        private Long strategyCaseId;
        private Long skuId;
        private String caseName;
        private StrategyCaseStatus caseStatus;
        private StrategyGenerationStage generationStage;
        private LocalDateTime resultExpiresAt;
        private String resultCacheKey;
        private Long requesterId;
        private String requesterName;
        private Long requesterOrganizationId;
    }

    @Getter
    @Setter
    public static class ExistingSelectionRecord {
        private Long finalSelectionId;
        private Long strategyOptionId;
        private Integer optionRank;
        private String constraintText;
    }

    @Getter
    @Setter
    public static class OptionWrite extends BaseEntity {
        private Long strategyOptionId;
        private Long strategyCaseId;
        private Integer optionRank;
        private String optionName;
        private String recommendationReason;
        private String advantageText;
        private String cautionText;
        private String constraintText;
    }

    @Getter
    @Setter
    public static class SimulationWrite extends BaseEntity {
        private Long simulationId;
        private Long strategyOptionId;
        private String inputSourceType;
        private BigDecimal targetQuantity;
        private BigDecimal strategyPrice;
        private BigDecimal movementCost;
        private BigDecimal expectedSalesQty;
        private BigDecimal expectedRevenue;
        private BigDecimal unitContributionMargin;
        private BigDecimal contributionMarginRate;
        private BigDecimal totalContributionMargin;
        private BigDecimal expectedRemainingQty;
        private Integer expectedSellThroughDays;
    }

    @Getter
    @Setter
    public static class ActionWrite extends BaseEntity {
        private Long strategyActionId;
        private Long strategyOptionId;
        private Long sourceSalesPointId;
        private Long targetSalesPointId;
        private Long sourceWarehouseId;
        private Long destinationWarehouseId;
        private StrategyType actionType;
        private BigDecimal actionQuantity;
        private BigDecimal strategyPrice;
        private BigDecimal discountRate;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal estimatedActionCost;
        private Integer actionOrder;
    }

    @Getter
    @Setter
    public static class LotAllocationWrite extends BaseEntity {
        private Long lotId;
        private BigDecimal allocatedQuantity;
        private Integer priorityNo;
        private Long sourceWarehouseId;
        private Long sourceSalesPointId;
        private Long strategyActionId;
    }

    @Getter
    @Setter
    public static class InventorySnapshotWrite extends BaseEntity {
        private Long strategyCaseId;
        private Long skuId;
        private Long lotId;
        private Long salesPointId;
        private Long inventoryBalanceId;
        private BigDecimal onTotalQty;
        private BigDecimal onHandQty;
        private BigDecimal safetyStockQty;
        private BigDecimal dailySalesVelocity;
        private BigDecimal forecastQty;
        private LocalDate expiryDate;
        private Long warehouseId;
    }

    @Getter
    @Setter
    public static class PriceSnapshotWrite extends BaseEntity {
        private Long strategyCaseId;
        private Long skuId;
        private Long salesPointId;
        private BigDecimal currentPrice;
        private BigDecimal productCost;
        private BigDecimal paymentFee;
        private BigDecimal logisticsCost;
        private BigDecimal unitVariableCost;
        private BigDecimal baselineUnitContributionMargin;
    }

    @Getter
    @Setter
    public static class FinalSelectionWrite extends BaseEntity {
        private Long finalSelectionId;
        private Long strategyCaseId;
        private Long strategyOptionId;
    }

    @Getter
    @Setter
    public static class ForecastSnapshotWrite extends BaseEntity {
        private Long finalSelectionId;
        private Long salesPointId;
        private Long modelVersionId;
        private String forecastRole;
        private String forecastRunId;
        private BigDecimal strategyPeriodPredictedQty;
        private BigDecimal forecast30dQty;
        private BigDecimal forecast60dQty;
        private BigDecimal forecast90dQty;
        private BigDecimal forecast180dQty;
        private String dailyForecastJson;
        private String inputDataHash;
        private LocalDateTime forecastGeneratedAt;
    }

    @Getter
    @Setter
    public static class ExecutionResultWrite extends BaseEntity {
        private Long strategyExecutionResultId;
        private Long finalSelectionId;
        private String resultStatus;
        private LocalDate plannedStartDate;
        private LocalDate plannedEndDate;
        private String goalMetricCode;
        private BigDecimal goalTargetValue;
        private BigDecimal startRiskStockQty;
        private BigDecimal startExpectedDisposalQty;
        private BigDecimal startUnitCost;
        private String calculationVersion;
    }

    @Getter
    @Setter
    public static class ReviewRequestWrite extends BaseEntity {
        private Long reviewRequestId;
        private Long strategyOptionId;
        private Long requesterId;
        private Long reviewerId;
        private StrategyReviewStatus reviewStatus;
        private String teamsMessageId;
        private String strategyUrl;
    }

    @Getter
    @Setter
    public static class ReviewRequestRecord {
        private Long reviewRequestId;
        private Long reviewerId;
        private StrategyReviewStatus reviewStatus;
    }
}
