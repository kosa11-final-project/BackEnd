package com.stockit.backend.feature.strategy.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateAssumption;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.vo.AiStrategyLotDisplayVO;
import com.stockit.backend.feature.strategy.vo.AiStrategySalesPointReferenceVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyWarehouseReferenceVO;

/** Redis 원본은 변경하지 않고 상세 화면에 필요한 표시명만 보강한 결과 DTO */
public record AiStrategyGenerationResultResponse(
        int schemaVersion,
        Long strategyCaseId,
        LocalDateTime generatedAt,
        BaselineSimulation baselineSimulation,
        List<Option> options,
        StrategyGenerationResult.NoRecommendation noRecommendation,
        StrategyGenerationResult.ProviderMetadata providerMetadata
) {

    /**
     * 생성 당시 계산 결과를 복사하면서 위치와 LOT의 화면 표시명만 보강
     *
     * <p>Redis 스키마를 화면 요구사항에 종속시키지 않기 위한 응답 계층 변환</p>
     */
    public static AiStrategyGenerationResultResponse from(
            StrategyGenerationResult result,
            Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
            Map<Long, AiStrategyWarehouseReferenceVO> warehouses,
            Map<Long, AiStrategyLotDisplayVO> lots
    ) {
        if (result == null) {
            return null;
        }
        return new AiStrategyGenerationResultResponse(
                result.schemaVersion(),
                result.strategyCaseId(),
                result.generatedAt(),
                result.baselineSimulation(),
                result.options().stream()
                        .map(option -> Option.from(
                                option, salesPoints, warehouses, lots
                        ))
                        .toList(),
                result.noRecommendation(),
                result.providerMetadata()
        );
    }

    public record Option(
            int rank,
            String optionName,
            String recommendationReason,
            String advantage,
            String caution,
            Candidate candidate,
            StrategyCandidateSimulation simulation
    ) {
        private static Option from(
                StrategyGenerationResult.Option option,
                Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
                Map<Long, AiStrategyWarehouseReferenceVO> warehouses,
                Map<Long, AiStrategyLotDisplayVO> lots
        ) {
            return new Option(
                    option.rank(),
                    option.optionName(),
                    option.recommendationReason(),
                    option.advantage(),
                    option.caution(),
                    Candidate.from(option.candidate(), salesPoints, warehouses, lots),
                    option.simulation()
            );
        }
    }

    public record Candidate(
            String candidateId,
            List<StrategyType> strategyTypes,
            LocalDate startDate,
            LocalDate endDate,
            List<Action> actions,
            List<CandidateAssumption> assumptions,
            StrategyGenerationResult.Preference preference,
            BigDecimal maxExecutableQty
    ) {
        private static Candidate from(
                StrategyGenerationResult.Candidate candidate,
                Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
                Map<Long, AiStrategyWarehouseReferenceVO> warehouses,
                Map<Long, AiStrategyLotDisplayVO> lots
        ) {
            return new Candidate(
                    candidate.candidateId(),
                    candidate.strategyTypes(),
                    candidate.startDate(),
                    candidate.endDate(),
                    candidate.actions().stream()
                            .map(action -> Action.from(
                                    action, salesPoints, warehouses, lots
                            ))
                            .toList(),
                    candidate.assumptions(),
                    candidate.preference(),
                    candidate.maxExecutableQty()
            );
        }
    }

    public record Action(
            StrategyType actionType,
            Location sourceLocation,
            Location targetLocation,
            BigDecimal actionQuantity,
            BigDecimal estimatedActionCost,
            BigDecimal strategyPrice,
            BigDecimal discountRate,
            List<LotAllocation> lotAllocations
    ) {
        private static Action from(
                StrategyGenerationResult.Action action,
                Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
                Map<Long, AiStrategyWarehouseReferenceVO> warehouses,
                Map<Long, AiStrategyLotDisplayVO> lots
        ) {
            return new Action(
                    action.actionType(),
                    Location.resolve(
                            action.sourceWarehouseId(),
                            action.sourceSalesPointId(),
                            salesPoints,
                            warehouses
                    ),
                    Location.resolve(
                            action.targetWarehouseId(),
                            action.targetSalesPointId(),
                            salesPoints,
                            warehouses
                    ),
                    action.actionQuantity(),
                    action.estimatedActionCost(),
                    action.strategyPrice(),
                    action.discountRate(),
                    action.lotAllocations().stream()
                            .map(allocation -> LotAllocation.from(allocation, lots))
                            .toList()
            );
        }
    }

    public record Location(
            LocationType locationType,
            Long locationId,
            String locationCode,
            String locationName
    ) {
        private static Location resolve(
                Long warehouseId,
                Long salesPointId,
                Map<Long, AiStrategySalesPointReferenceVO> salesPoints,
                Map<Long, AiStrategyWarehouseReferenceVO> warehouses
        ) {
            // 할당 재고가 창고와 판매처를 함께 가질 때 화면 업무 위치인 판매처 우선
            // 판매처가 없는 공용 미할당 재고만 창고 위치로 표시
            if (salesPointId != null) {
                AiStrategySalesPointReferenceVO point = salesPoints.get(salesPointId);
                return new Location(
                        LocationType.SALES_POINT,
                        salesPointId,
                        point == null ? null : point.getSalesPointCode(),
                        point == null ? null : point.getSalesPointName()
                );
            }
            if (warehouseId != null) {
                AiStrategyWarehouseReferenceVO warehouse = warehouses.get(warehouseId);
                return new Location(
                        LocationType.WAREHOUSE,
                        warehouseId,
                        warehouse == null ? null : warehouse.getWarehouseCode(),
                        warehouse == null ? null : warehouse.getWarehouseName()
                );
            }
            return null;
        }
    }

    public enum LocationType {
        SALES_POINT,
        WAREHOUSE
    }

    public record LotAllocation(
            Long inventoryBalanceId,
            Long lotId,
            String lotCode,
            BigDecimal quantity,
            int priorityNo
    ) {
        private static LotAllocation from(
                StrategyGenerationResult.LotAllocation allocation,
                Map<Long, AiStrategyLotDisplayVO> lots
        ) {
            AiStrategyLotDisplayVO lot = lots.get(allocation.lotId());
            return new LotAllocation(
                    allocation.inventoryBalanceId(),
                    allocation.lotId(),
                    lot == null ? null : lot.getLotCode(),
                    allocation.quantity(),
                    allocation.priorityNo()
            );
        }
    }
}
