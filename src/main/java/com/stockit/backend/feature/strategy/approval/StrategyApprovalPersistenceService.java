package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ActionWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.CaseRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ExistingSelectionRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.FinalSelectionWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ForecastSnapshotWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.InventorySnapshotWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.LotAllocationWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.OptionWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.PriceSnapshotWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.SimulationWrite;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

/** Redis의 선택 후보와 계산 스냅샷을 승인 요청 시점에 DB로 확정한다. */
@Service
public class StrategyApprovalPersistenceService {

    private static final int TEXT_LIMIT = 2_000;

    private final StrategyApprovalMapper approvalMapper;
    private final ObjectMapper objectMapper;

    public StrategyApprovalPersistenceService(
            StrategyApprovalMapper approvalMapper,
            ObjectMapper objectMapper
    ) {
        this.approvalMapper = approvalMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PreparedStrategyApproval prepare(
            Long strategyCaseId,
            Long actorId,
            Long organizationId,
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context,
            List<AiStrategyReviewerVO> reviewers
    ) {
        CaseRecord strategyCase = approvalMapper.selectCaseForUpdate(strategyCaseId);
        if (strategyCase == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_CASE_NOT_FOUND);
        }
        if (!Objects.equals(
                organizationId, strategyCase.getRequesterOrganizationId()
        )) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (strategyCase.getCaseStatus() != StrategyCaseStatus.GENERATED
                && strategyCase.getCaseStatus() != StrategyCaseStatus.READY_TO_EXECUTE) {
            throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);
        }
        if (!Objects.equals(strategyCaseId, context.strategyCaseId())) {
            throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);
        }

        ExistingSelectionRecord existing =
                approvalMapper.selectExistingSelection(strategyCaseId);
        SelectionIds selectionIds;
        if (existing == null) {
            if (strategyCase.getCaseStatus() != StrategyCaseStatus.GENERATED) {
                throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);
            }
            selectionIds = persistSelection(
                    strategyCase, actorId, option, context
            );
        } else {
            if (!Objects.equals(existing.getOptionRank(), option.rank())) {
                throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);
            }
            selectionIds = new SelectionIds(
                    existing.getFinalSelectionId(),
                    existing.getStrategyOptionId()
            );
        }

        List<Long> reviewerIds = reviewers.stream()
                .map(AiStrategyReviewerVO::getReviewerId)
                .toList();
        Map<Long, ReviewRequestRecord> existingRequests = approvalMapper
                .selectReviewRequests(selectionIds.strategyOptionId(), reviewerIds)
                .stream()
                .collect(Collectors.toMap(
                        ReviewRequestRecord::getReviewerId,
                        request -> request
                ));

        for (AiStrategyReviewerVO reviewer : reviewers) {
            if (existingRequests.containsKey(reviewer.getReviewerId())) {
                continue;
            }
            if (strategyCase.getCaseStatus() == StrategyCaseStatus.READY_TO_EXECUTE) {
                throw new AppException(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);
            }
            ReviewRequestWrite request = new ReviewRequestWrite();
            request.setStrategyOptionId(selectionIds.strategyOptionId());
            request.setRequesterId(actorId);
            request.setReviewerId(reviewer.getReviewerId());
            request.setReviewStatus(StrategyReviewStatus.PENDING);
            audit(request, actorId);
            approvalMapper.insertReviewRequest(request);
        }

        List<ReviewRequestRecord> reviewRequests = approvalMapper
                .selectReviewRequests(selectionIds.strategyOptionId(), reviewerIds);
        if (reviewRequests.size() != reviewerIds.size()) {
            throw new AppException(ErrorCode.DATABASE_ERROR);
        }
        return new PreparedStrategyApproval(
                strategyCaseId,
                selectionIds.finalSelectionId(),
                selectionIds.strategyOptionId(),
                strategyCase.getCaseStatus(),
                strategyCase.getCaseName(),
                option,
                context,
                reviewRequests
        );
    }

    private SelectionIds persistSelection(
            CaseRecord strategyCase,
            Long actorId,
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        OptionWrite optionWrite = optionWrite(strategyCase, actorId, option);
        approvalMapper.insertOption(optionWrite);

        approvalMapper.insertSimulation(simulationWrite(
                optionWrite.getStrategyOptionId(), actorId, option
        ));
        persistActions(optionWrite.getStrategyOptionId(), actorId, option);
        persistInventorySnapshots(strategyCase, actorId, option, context);
        persistPriceSnapshots(strategyCase, actorId, option, context);

        FinalSelectionWrite finalSelection = new FinalSelectionWrite();
        finalSelection.setStrategyCaseId(strategyCase.getStrategyCaseId());
        finalSelection.setStrategyOptionId(optionWrite.getStrategyOptionId());
        audit(finalSelection, actorId);
        approvalMapper.insertFinalSelection(finalSelection);

        persistForecastSnapshots(
                finalSelection.getFinalSelectionId(), actorId, option, context
        );
        return new SelectionIds(
                finalSelection.getFinalSelectionId(),
                optionWrite.getStrategyOptionId()
        );
    }

    private OptionWrite optionWrite(
            CaseRecord strategyCase,
            Long actorId,
            StrategyGenerationResult.Option option
    ) {
        OptionWrite write = new OptionWrite();
        write.setStrategyCaseId(strategyCase.getStrategyCaseId());
        write.setOptionRank(option.rank());
        write.setOptionName(option.optionName());
        write.setRecommendationReason(truncate(option.recommendationReason()));
        write.setAdvantageText(truncate(option.advantage()));
        write.setCautionText(truncate(option.caution()));
        write.setConstraintText(truncate(option.candidate().assumptions().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","))));
        audit(write, actorId);
        return write;
    }

    private SimulationWrite simulationWrite(
            Long strategyOptionId,
            Long actorId,
            StrategyGenerationResult.Option option
    ) {
        StrategyCandidateSimulation.Summary summary = option.simulation().summary();
        SimulationWrite write = new SimulationWrite();
        write.setStrategyOptionId(strategyOptionId);
        write.setInputSourceType("AI_RECOMMENDED");
        write.setTargetQuantity(option.candidate().maxExecutableQty());
        write.setStrategyPrice(firstNonNullStrategyPrice(option.candidate()));
        write.setMovementCost(option.candidate().actions().stream()
                .filter(action -> action.actionType() == StrategyType.REALLOCATION
                        || action.actionType() == StrategyType.RT_TRANSFER)
                .map(StrategyGenerationResult.Action::estimatedActionCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        write.setExpectedSalesQty(summary.expectedSalesQty());
        write.setExpectedRevenue(summary.expectedRevenue());
        write.setUnitContributionMargin(divide(
                summary.totalContributionMargin(), summary.expectedSalesQty()
        ));
        write.setContributionMarginRate(summary.contributionMarginRate());
        write.setTotalContributionMargin(summary.totalContributionMargin());
        write.setExpectedRemainingQty(summary.expectedRemainingQty());
        write.setExpectedSellThroughDays(summary.expectedSellThroughDays());
        audit(write, actorId);
        return write;
    }

    private void persistActions(
            Long strategyOptionId,
            Long actorId,
            StrategyGenerationResult.Option option
    ) {
        int order = 1;
        for (StrategyGenerationResult.Action action : option.candidate().actions()) {
            ActionWrite write = new ActionWrite();
            write.setStrategyOptionId(strategyOptionId);
            write.setSourceWarehouseId(action.sourceWarehouseId());
            write.setSourceSalesPointId(action.sourceSalesPointId());
            write.setDestinationWarehouseId(action.targetWarehouseId());
            write.setTargetSalesPointId(action.targetSalesPointId());
            write.setActionType(action.actionType());
            write.setActionQuantity(action.actionQuantity());
            write.setStrategyPrice(action.strategyPrice());
            write.setDiscountRate(action.discountRate());
            write.setStartDate(option.candidate().startDate());
            write.setEndDate(option.candidate().endDate());
            write.setEstimatedActionCost(action.estimatedActionCost());
            write.setActionOrder(order++);
            audit(write, actorId);
            approvalMapper.insertAction(write);

            for (StrategyGenerationResult.LotAllocation allocation
                    : action.lotAllocations()) {
                LotAllocationWrite allocationWrite = new LotAllocationWrite();
                allocationWrite.setLotId(allocation.lotId());
                allocationWrite.setAllocatedQuantity(allocation.quantity());
                allocationWrite.setPriorityNo(allocation.priorityNo());
                allocationWrite.setSourceWarehouseId(action.sourceWarehouseId());
                allocationWrite.setSourceSalesPointId(action.sourceSalesPointId());
                allocationWrite.setStrategyActionId(write.getStrategyActionId());
                audit(allocationWrite, actorId);
                approvalMapper.insertLotAllocation(allocationWrite);
            }
        }
    }

    private void persistInventorySnapshots(
            CaseRecord strategyCase,
            Long actorId,
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        for (StrategyCalculationContext.InventoryLot inventory
                : context.evaluationInventory()) {
            InventorySnapshotWrite write = new InventorySnapshotWrite();
            Long salesPointId = inventory.effectiveSalesPointId();
            write.setStrategyCaseId(strategyCase.getStrategyCaseId());
            write.setSkuId(strategyCase.getSkuId());
            write.setLotId(inventory.lotId());
            write.setSalesPointId(salesPointId);
            write.setInventoryBalanceId(inventory.inventoryBalanceId());
            write.setOnTotalQty(inventory.availableQty().add(inventory.reservedQty()));
            write.setOnHandQty(inventory.availableQty());
            write.setSafetyStockQty(safetyStock(context, inventory));
            write.setDailySalesVelocity(averageForecast(context, salesPointId));
            write.setForecastQty(periodForecast(
                    context, salesPointId,
                    option.candidate().startDate(), periodEnd(option, context)
            ));
            write.setExpiryDate(inventory.expiryDate());
            write.setWarehouseId(inventory.warehouseId());
            audit(write, actorId);
            approvalMapper.insertInventorySnapshot(write);
        }
    }

    private void persistPriceSnapshots(
            CaseRecord strategyCase,
            Long actorId,
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        for (Long salesPointId : relevantSalesPointIds(option, context)) {
            StrategyCalculationContext.SalesPoint salesPoint =
                    context.salesPoints().get(salesPointId);
            if (salesPoint == null || salesPoint.price() == null) {
                continue;
            }
            StrategyCalculationContext.Price price = salesPoint.price();
            BigDecimal unitVariableCost = context.unitCost()
                    .add(price.paymentFee())
                    .add(price.logisticsCost());
            PriceSnapshotWrite write = new PriceSnapshotWrite();
            write.setStrategyCaseId(strategyCase.getStrategyCaseId());
            write.setSkuId(strategyCase.getSkuId());
            write.setSalesPointId(salesPointId);
            write.setCurrentPrice(price.actualPrice());
            write.setProductCost(context.unitCost());
            write.setPaymentFee(price.paymentFee());
            write.setLogisticsCost(price.logisticsCost());
            write.setUnitVariableCost(unitVariableCost);
            write.setBaselineUnitContributionMargin(
                    price.actualPrice().subtract(unitVariableCost)
            );
            audit(write, actorId);
            approvalMapper.insertPriceSnapshot(write);
        }
    }

    private void persistForecastSnapshots(
            Long finalSelectionId,
            Long actorId,
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        Map<String, Set<Long>> roles = new LinkedHashMap<>();
        roles.put("SOURCE", sourceSalesPointIds(option, context));
        roles.put("TARGET", targetSalesPointIds(option));
        LocalDate periodEnd = periodEnd(option, context);

        for (Map.Entry<String, Set<Long>> role : roles.entrySet()) {
            for (Long salesPointId : role.getValue()) {
                StrategyCalculationContext.SalesPoint salesPoint =
                        context.salesPoints().get(salesPointId);
                if (salesPoint == null) {
                    continue;
                }
                String dailyJson = dailyForecastJson(
                        salesPoint.dailyForecast(),
                        option.candidate().startDate(),
                        periodEnd
                );
                ForecastSnapshotWrite write = new ForecastSnapshotWrite();
                write.setFinalSelectionId(finalSelectionId);
                write.setSalesPointId(salesPointId);
                write.setModelVersionId(context.forecastMetadata().modelVersionId());
                write.setForecastRole(role.getKey());
                write.setForecastRunId(context.forecastMetadata().forecastRunId());
                write.setStrategyPeriodPredictedQty(sumForecast(
                        salesPoint.dailyForecast(),
                        option.candidate().startDate(), periodEnd
                ));
                write.setForecast30dQty(horizonForecast(context, salesPoint, 30));
                write.setForecast60dQty(horizonForecast(context, salesPoint, 60));
                write.setForecast90dQty(horizonForecast(context, salesPoint, 90));
                write.setForecast180dQty(horizonForecast(context, salesPoint, 180));
                write.setDailyForecastJson(dailyJson);
                write.setInputDataHash(sha256(
                        context.forecastMetadata().forecastRunId()
                                + ":" + salesPointId + ":" + dailyJson
                ));
                write.setForecastGeneratedAt(
                        context.forecastMetadata().forecastGeneratedAt().toLocalDateTime()
                );
                audit(write, actorId);
                approvalMapper.insertForecastSnapshot(write);
            }
        }
    }

    private String dailyForecastJson(
            Map<LocalDate, BigDecimal> forecasts,
            LocalDate start,
            LocalDate end
    ) {
        List<DailyForecastValue> values = forecasts.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(start)
                        && !entry.getKey().isAfter(end))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DailyForecastValue(entry.getKey(), entry.getValue()))
                .toList();
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private static Set<Long> relevantSalesPointIds(
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        Set<Long> values = new LinkedHashSet<>();
        if (context.sourceSalesPointId() != null) {
            values.add(context.sourceSalesPointId());
        }
        option.candidate().actions().forEach(action -> {
            if (action.sourceSalesPointId() != null) {
                values.add(action.sourceSalesPointId());
            }
            if (action.targetSalesPointId() != null) {
                values.add(action.targetSalesPointId());
            }
        });
        return values;
    }

    private static Set<Long> sourceSalesPointIds(
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        Set<Long> values = option.candidate().actions().stream()
                .map(StrategyGenerationResult.Action::sourceSalesPointId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (values.isEmpty() && context.sourceSalesPointId() != null) {
            values.add(context.sourceSalesPointId());
        }
        return values;
    }

    private static Set<Long> targetSalesPointIds(
            StrategyGenerationResult.Option option
    ) {
        return option.candidate().actions().stream()
                .map(StrategyGenerationResult.Action::targetSalesPointId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static BigDecimal safetyStock(
            StrategyCalculationContext context,
            StrategyCalculationContext.InventoryLot inventory
    ) {
        return context.inventoryPolicies().stream()
                .filter(policy -> Objects.equals(
                        policy.warehouseId(), inventory.warehouseId()
                ))
                .filter(policy -> policy.stockSalesPointId() == null
                        || Objects.equals(
                        policy.stockSalesPointId(), inventory.stockSalesPointId()
                ))
                .filter(policy -> policy.allocatedSalesPointId() == null
                        || Objects.equals(
                        policy.allocatedSalesPointId(),
                        inventory.allocatedSalesPointId()
                ))
                .map(StrategyCalculationContext.InventoryPolicy::safetyStockQty)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal averageForecast(
            StrategyCalculationContext context,
            Long salesPointId
    ) {
        if (salesPointId == null) {
            return null;
        }
        StrategyCalculationContext.SalesPoint salesPoint =
                context.salesPoints().get(salesPointId);
        if (salesPoint == null || salesPoint.dailyForecast().isEmpty()) {
            return null;
        }
        BigDecimal total = salesPoint.dailyForecast().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(
                BigDecimal.valueOf(salesPoint.dailyForecast().size()),
                3,
                RoundingMode.HALF_UP
        );
    }

    private static BigDecimal periodForecast(
            StrategyCalculationContext context,
            Long salesPointId,
            LocalDate start,
            LocalDate end
    ) {
        if (salesPointId == null || !context.salesPoints().containsKey(salesPointId)) {
            return null;
        }
        return sumForecast(
                context.salesPoints().get(salesPointId).dailyForecast(), start, end
        );
    }

    private static BigDecimal horizonForecast(
            StrategyCalculationContext context,
            StrategyCalculationContext.SalesPoint salesPoint,
            int days
    ) {
        LocalDate end = context.forecastStartDate().plusDays(days - 1L);
        if (context.forecastEndDate().isBefore(end)) {
            return null;
        }
        return sumForecast(
                salesPoint.dailyForecast(), context.forecastStartDate(), end
        );
    }

    private static BigDecimal sumForecast(
            Map<LocalDate, BigDecimal> forecasts,
            LocalDate start,
            LocalDate end
    ) {
        return forecasts.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(start)
                        && !entry.getKey().isAfter(end))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate periodEnd(
            StrategyGenerationResult.Option option,
            StrategyCalculationContext context
    ) {
        return option.candidate().endDate() == null
                ? context.strategyEndDate()
                : option.candidate().endDate();
    }

    private static BigDecimal firstNonNullStrategyPrice(
            StrategyGenerationResult.Candidate candidate
    ) {
        return candidate.actions().stream()
                .map(StrategyGenerationResult.Action::strategyPrice)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= TEXT_LIMIT) {
            return value;
        }
        return value.substring(0, TEXT_LIMIT);
    }

    private static void audit(
            com.stockit.backend.common.persistence.BaseEntity entity,
            Long actorId
    ) {
        entity.setCreatedBy(actorId);
        entity.setUpdatedBy(actorId);
        entity.setIsDeleted(false);
    }

    private record SelectionIds(Long finalSelectionId, Long strategyOptionId) {
    }

    private record DailyForecastValue(LocalDate date, BigDecimal predictedQty) {
    }
}
