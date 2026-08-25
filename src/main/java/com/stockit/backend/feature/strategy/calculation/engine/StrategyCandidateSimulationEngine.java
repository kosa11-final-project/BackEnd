package com.stockit.backend.feature.strategy.calculation.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SourceInventoryCapacityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.TargetAdditionalDemandPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.SimulationDetailLevel;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.InventoryLot;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.Price;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountDemandPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 후보 액션과 판매처별 수요를 일자별 재고에 반영해 무전략 대비 효과를 계산하는 엔진
 */
@Component
public class StrategyCandidateSimulationEngine {

    private static final BigDecimal ZERO_QUANTITY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.QUANTITY_SCALE
    );
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.MONEY_SCALE
    );

    private final TargetAdditionalDemandPolicy targetDemandPolicy;
    private final SourceInventoryCapacityPolicy sourceCapacityPolicy;
    private final DiscountDemandPolicy discountDemandPolicy;

    public StrategyCandidateSimulationEngine(
            TargetAdditionalDemandPolicy targetDemandPolicy,
            SourceInventoryCapacityPolicy sourceCapacityPolicy,
            DiscountDemandPolicy discountDemandPolicy
    ) {
        this.targetDemandPolicy = targetDemandPolicy;
        this.sourceCapacityPolicy = sourceCapacityPolicy;
        this.discountDemandPolicy = discountDemandPolicy;
    }

    /**
     * 전략 시작 전 정상 판매와 시작 후 액션을 하나의 일별 흐름으로 시뮬레이션한다
     *
     * <p>후보 생성 시 계산한 예상 잔여 수량을 신뢰하되, 실제 일별 흐름에서도
     * 시작 시점에 같은 수량이 남아 있는지 재검증한다</p>
     */
    public StrategyCandidateSimulation simulate(
            StrategyCalculationContext context,
            StrategyCandidate candidate,
            BaselineSimulation baseline,
            SimulationDetailLevel detailLevel
    ) {
        validateRange(context, candidate);
        CandidatePlan plan = CandidatePlan.create(candidate);
        List<LotState> lots = plan.createLotStates(context.evaluationInventory());
        Map<Long, Map<LocalDate, BigDecimal>> demandBySalesPoint = demandBySalesPoint(
                context,
                plan,
                candidate.startDate(),
                candidate.endDate() == null
                        ? context.strategyEndDate()
                        : candidate.endDate()
        );

        BigDecimal cumulativeSales = ZERO_QUANTITY;
        BigDecimal cumulativeRevenue = ZERO_MONEY;
        BigDecimal cumulativeContributionMargin = ZERO_MONEY;
        BigDecimal cumulativeDisposal = ZERO_QUANTITY;
        BigDecimal allocatedDisposal = ZERO_QUANTITY;
        Integer sellThroughDays = null;
        boolean strategyApplied = false;
        List<StrategyCandidateSimulation.DailyPoint> dailySeries = new ArrayList<>();

        for (LocalDate date = context.forecastStartDate();
                !date.isAfter(context.forecastEndDate());
                date = date.plusDays(1)) {
            DisposalResult disposal = disposeExpired(lots, date);
            cumulativeDisposal = quantity(cumulativeDisposal.add(disposal.total()));
            allocatedDisposal = quantity(allocatedDisposal.add(disposal.allocated()));

            // 시작일 당일 수요에는 이동·할인 조건이 적용되도록 판매 전에 액션 반영
            if (date.equals(candidate.startDate())) {
                applyStrategy(plan, lots, date);
                strategyApplied = true;
            }

            DailySales dailySales = sellDaily(
                    context,
                    candidate,
                    plan,
                    lots,
                    demandBySalesPoint,
                    date
            );
            cumulativeSales = quantity(cumulativeSales.add(dailySales.quantity()));
            cumulativeRevenue = money(cumulativeRevenue.add(dailySales.revenue()));
            cumulativeContributionMargin = money(
                    cumulativeContributionMargin.add(
                            dailySales.contributionMargin()
                    )
            );

            if (strategyApplied && sellThroughDays == null
                    && allocatedDisposal.signum() == 0
                    && allocatedRemaining(lots).signum() == 0) {
                sellThroughDays = Math.toIntExact(
                        ChronoUnit.DAYS.between(candidate.startDate(), date) + 1
                );
            }

            if (detailLevel == SimulationDetailLevel.WITH_DAILY_SERIES) {
                dailySeries.add(new StrategyCandidateSimulation.DailyPoint(
                        date,
                        dailySales.quantity(),
                        totalRemaining(lots),
                        cumulativeRevenue,
                        cumulativeContributionMargin
                ));
            }
        }

        if (!strategyApplied) {
            throw new CandidateSimulationException(
                    "CANDIDATE_START_OUT_OF_RANGE",
                    "Candidate strategy start date was not reached"
            );
        }

        BigDecimal actionCost = estimatedActionCost(candidate);
        BigDecimal contributionMarginRate = cumulativeRevenue.signum() == 0
                ? BigDecimal.ZERO.setScale(CalculationPrecisionPolicy.RATE_SCALE)
                : CalculationPrecisionPolicy.rate(
                        cumulativeContributionMargin.divide(
                                cumulativeRevenue,
                                CalculationPrecisionPolicy.RATE_SCALE,
                                RoundingMode.HALF_UP
                        )
                );
        BigDecimal contributionMarginDelta = money(
                cumulativeContributionMargin.subtract(
                        baseline.summary().totalContributionMargin()
                )
        );
        BigDecimal netEffect = money(contributionMarginDelta.subtract(actionCost));
        StrategyCandidateSimulation.Summary summary =
                new StrategyCandidateSimulation.Summary(
                        cumulativeSales,
                        cumulativeRevenue,
                        cumulativeContributionMargin,
                        contributionMarginRate,
                        sellThroughDays,
                        totalRemaining(lots),
                        cumulativeDisposal,
                        actionCost,
                        netEffect
                );
        StrategyCandidateSimulation.ComparisonToBaseline comparison =
                new StrategyCandidateSimulation.ComparisonToBaseline(
                        quantity(cumulativeSales.subtract(
                                baseline.summary().expectedSalesQty()
                        )),
                        money(cumulativeRevenue.subtract(
                                baseline.summary().expectedRevenue()
                        )),
                        contributionMarginDelta,
                        quantity(baseline.summary().expectedRemainingQty()
                                .subtract(totalRemaining(lots))),
                        quantity(baseline.summary().expectedDisposalQty()
                                .subtract(cumulativeDisposal)),
                        netEffect
                );
        return new StrategyCandidateSimulation(
                candidate.candidateId(),
                summary,
                comparison,
                dailySeries,
                candidate.assumptions()
        );
    }

    private Map<Long, Map<LocalDate, BigDecimal>> demandBySalesPoint(
            StrategyCalculationContext context,
            CandidatePlan plan,
            LocalDate strategyStartDate,
            LocalDate strategyEndDate
    ) {
        Set<Long> salesPointIds = new LinkedHashSet<>();
        if (context.sourceSalesPointId() != null) {
            salesPointIds.add(context.sourceSalesPointId());
        }
        plan.allocations().values().stream()
                .map(AllocationPlan::targetSalesPointId)
                .filter(Objects::nonNull)
                .forEach(salesPointIds::add);

        StrategyCalculationContext projectedContext = context;
        boolean hasMovementTarget = salesPointIds.stream().anyMatch(salesPointId ->
                !Objects.equals(salesPointId, context.sourceSalesPointId()));
        if (hasMovementTarget) {
            // 대상 판매처도 전략 시작 전 정상 판매를 반영한 동일 시점의 재고로 추가 수요 계산
            SourceInventoryCapacityPolicy.Projection projection =
                    sourceCapacityPolicy.projectAt(context, strategyStartDate);
            projectedContext = context.withInventory(
                    projection.evaluationInventory(),
                    projection.referenceInventory()
            );
        }

        Map<Long, Map<LocalDate, BigDecimal>> result = new LinkedHashMap<>();
        for (Long salesPointId : salesPointIds) {
            Map<LocalDate, BigDecimal> demand;
            if (Objects.equals(salesPointId, context.sourceSalesPointId())) {
                demand = requiredForecast(context, salesPointId);
            } else {
                demand = completeForecastRange(
                        context,
                        targetDemand(
                                projectedContext,
                                salesPointId,
                                strategyStartDate,
                                strategyEndDate
                        )
                );
            }
            BigDecimal discountRate = plan.discountRate(salesPointId);
            result.put(
                    salesPointId,
                    discountRate == null
                            ? demand
                            : applyDiscountDemand(
                                    context,
                                    salesPointId,
                                    demand,
                                    discountRate,
                                    strategyStartDate,
                                    strategyEndDate
                            )
            );
        }
        return result;
    }

    private Map<LocalDate, BigDecimal> applyDiscountDemand(
            StrategyCalculationContext context,
            Long salesPointId,
            Map<LocalDate, BigDecimal> baselineDemand,
            BigDecimal discountRate,
            LocalDate strategyStartDate,
            LocalDate strategyEndDate
    ) {
        SalesPoint salesPoint = context.salesPoints().get(salesPointId);
        if (salesPoint == null) {
            throw new CandidateSimulationException(
                    "CALCULATION_SALES_POINT_NOT_FOUND",
                    "Sales point is missing from calculation context: " + salesPointId
            );
        }
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        baselineDemand.forEach((date, demand) -> result.put(
                date,
                !date.isBefore(strategyStartDate) && !date.isAfter(strategyEndDate)
                        ? discountDemandPolicy.apply(demand, salesPoint, discountRate)
                        : demand
        ));
        return result;
    }

    private Map<LocalDate, BigDecimal> targetDemand(
            StrategyCalculationContext context,
            Long salesPointId,
            LocalDate strategyStartDate,
            LocalDate strategyEndDate
    ) {
        try {
            return targetDemandPolicy.calculate(
                    context,
                    salesPointId,
                    strategyStartDate,
                    strategyEndDate
            );
        } catch (StrategyCalculationException exception) {
            throw new CandidateSimulationException(
                    exception.getCode(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private static Map<LocalDate, BigDecimal> completeForecastRange(
            StrategyCalculationContext context,
            Map<LocalDate, BigDecimal> strategyPeriodDemand
    ) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (LocalDate date = context.forecastStartDate();
                !date.isAfter(context.forecastEndDate());
                date = date.plusDays(1)) {
            result.put(date, strategyPeriodDemand.getOrDefault(date, ZERO_QUANTITY));
        }
        return result;
    }

    private static Map<LocalDate, BigDecimal> requiredForecast(
            StrategyCalculationContext context,
            Long salesPointId
    ) {
        SalesPoint salesPoint = context.salesPoints().get(salesPointId);
        if (salesPoint == null) {
            throw new StrategyCalculationException(
                    "CALCULATION_SALES_POINT_NOT_FOUND",
                    "Sales point is missing from calculation context: " + salesPointId
            );
        }
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (LocalDate date = context.forecastStartDate();
                !date.isAfter(context.forecastEndDate());
                date = date.plusDays(1)) {
            BigDecimal predicted = salesPoint.dailyForecast().get(date);
            if (predicted == null || predicted.signum() < 0) {
                throw new StrategyCalculationException(
                        "CALCULATION_FORECAST_INVALID",
                        "Daily forecast is missing or negative: " + date
                );
            }
            result.put(date, quantity(predicted));
        }
        return result;
    }

    private static DailySales sellDaily(
            StrategyCalculationContext context,
            StrategyCandidate candidate,
            CandidatePlan plan,
            List<LotState> lots,
            Map<Long, Map<LocalDate, BigDecimal>> demandBySalesPoint,
            LocalDate date
    ) {
        BigDecimal quantity = ZERO_QUANTITY;
        BigDecimal revenue = ZERO_MONEY;
        BigDecimal contributionMargin = ZERO_MONEY;

        for (Map.Entry<Long, Map<LocalDate, BigDecimal>> entry
                : demandBySalesPoint.entrySet()) {
            BigDecimal remainingDemand = entry.getValue().get(date);
            if (remainingDemand == null) {
                throw new CandidateSimulationException(
                        "CALCULATION_FORECAST_INVALID",
                        "Daily forecast is missing: " + date
                );
            }
            List<LotState> sellable = lots.stream()
                    .filter(lot -> Objects.equals(lot.salesPointId, entry.getKey()))
                    .filter(lot -> lot.isSellableAt(date, candidate))
                    .sorted(LotState.OUTBOUND_ORDER)
                    .toList();
            for (LotState lot : sellable) {
                if (remainingDemand.signum() <= 0) {
                    break;
                }
                BigDecimal sold = lot.remaining.min(remainingDemand);
                CommercialTerms terms = commercialTerms(
                        context,
                        candidate,
                        plan,
                        lot,
                        date
                );
                BigDecimal unitContribution = terms.sellingPrice()
                        .subtract(context.unitCost())
                        .subtract(terms.paymentFee())
                        .subtract(terms.logisticsCost());
                lot.remaining = quantity(lot.remaining.subtract(sold));
                remainingDemand = quantity(remainingDemand.subtract(sold));
                quantity = quantity(quantity.add(sold));
                revenue = money(revenue.add(sold.multiply(terms.sellingPrice())));
                contributionMargin = money(contributionMargin.add(
                        sold.multiply(unitContribution)
                ));
            }
        }
        return new DailySales(quantity, revenue, contributionMargin);
    }

    private static CommercialTerms commercialTerms(
            StrategyCalculationContext context,
            StrategyCandidate candidate,
            CandidatePlan plan,
            LotState lot,
            LocalDate date
    ) {
        Price pointPrice = null;
        SalesPoint point = context.salesPoints().get(lot.salesPointId);
        if (point != null) {
            pointPrice = point.price();
        }

        BigDecimal sellingPrice;
        BigDecimal paymentFee;
        BigDecimal logisticsCost;
        if (lot.strategyAllocated && lot.strategyApplied
                && plan.channelTerms() != null) {
            sellingPrice = plan.channelTerms().sellingPrice();
            paymentFee = plan.channelTerms().paymentFee();
            logisticsCost = plan.channelTerms().logisticsCost();
        } else {
            if (pointPrice == null) {
                throw new CandidateSimulationException(
                        "CANDIDATE_PRICE_NOT_FOUND",
                        "Commercial terms are missing for sales point: "
                                + lot.salesPointId
                );
            }
            sellingPrice = pointPrice.actualPrice();
            paymentFee = pointPrice.paymentFee();
            logisticsCost = pointPrice.logisticsCost();
        }

        if (lot.strategyAllocated && lot.strategyApplied
                && lot.discountPrice != null
                && !date.isBefore(candidate.startDate())
                && (candidate.endDate() == null
                || !date.isAfter(candidate.endDate()))) {
            sellingPrice = lot.discountPrice;
        }
        return new CommercialTerms(sellingPrice, paymentFee, logisticsCost);
    }

    private static DisposalResult disposeExpired(
            List<LotState> lots,
            LocalDate date
    ) {
        BigDecimal total = ZERO_QUANTITY;
        BigDecimal allocated = ZERO_QUANTITY;
        for (LotState lot : lots) {
            if (lot.isExpiredAt(date) && lot.remaining.signum() > 0) {
                total = total.add(lot.remaining);
                if (lot.strategyAllocated) {
                    allocated = allocated.add(lot.remaining);
                }
                lot.remaining = ZERO_QUANTITY;
            }
        }
        return new DisposalResult(quantity(total), quantity(allocated));
    }

    private static void applyStrategy(
            CandidatePlan plan,
            List<LotState> lots,
            LocalDate date
    ) {
        // 시작일까지 정상 판매되고 남은 원본 LOT에서만 전략 적용분 분리
        List<LotState> allocatedLots = new ArrayList<>();
        for (AllocationPlan allocation : plan.allocations().values()) {
            LotState source = lots.stream()
                    .filter(lot -> !lot.strategyAllocated)
                    .filter(lot -> Objects.equals(
                            lot.input.inventoryBalanceId(),
                            allocation.inventoryBalanceId()
                    ))
                    .findFirst()
                    .orElseThrow(() -> new CandidateSimulationException(
                            "CANDIDATE_ALLOCATION_NOT_FOUND",
                            "Candidate allocation is outside evaluation inventory"
                    ));
            if (!Objects.equals(source.input.lotId(), allocation.lotId())
                    || source.remaining.compareTo(allocation.quantity()) < 0
                    || !source.isBaseSellableAt(date)) {
                throw new CandidateSimulationException(
                        "CANDIDATE_PROJECTED_INVENTORY_UNAVAILABLE",
                        "Projected candidate inventory is unavailable at strategy start"
                );
            }

            source.remaining = quantity(source.remaining.subtract(
                    allocation.quantity()
            ));
            LotState allocated = new LotState(
                    source.input,
                    allocation.quantity(),
                    true
            );
            if (allocation.targetWarehouseId() != null) {
                allocated.warehouseId = allocation.targetWarehouseId();
            }
            if (allocation.targetSalesPointId() != null) {
                allocated.salesPointId = allocation.targetSalesPointId();
            }
            allocated.discountPrice = allocation.discountPrice();
            allocated.strategyApplied = true;
            allocatedLots.add(allocated);
        }
        lots.addAll(allocatedLots);
    }

    private static void validateRange(
            StrategyCalculationContext context,
            StrategyCandidate candidate
    ) {
        if (candidate.startDate().isBefore(context.forecastStartDate())
                || candidate.startDate().isAfter(context.forecastEndDate())
                || (candidate.endDate() != null
                && candidate.endDate().isAfter(context.forecastEndDate()))) {
            throw new CandidateSimulationException(
                    "CANDIDATE_PERIOD_OUT_OF_RANGE",
                    "Candidate period must be inside the forecast range"
            );
        }
    }

    private static BigDecimal estimatedActionCost(StrategyCandidate candidate) {
        return money(candidate.actions().stream()
                .map(StrategyCandidate.Action::estimatedActionCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal totalRemaining(List<LotState> lots) {
        return quantity(lots.stream()
                .map(lot -> lot.remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal allocatedRemaining(List<LotState> lots) {
        return quantity(lots.stream()
                .filter(lot -> lot.strategyAllocated)
                .map(lot -> lot.remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal quantity(BigDecimal value) {
        return CalculationPrecisionPolicy.quantity(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return CalculationPrecisionPolicy.money(value);
    }

    private record DailySales(
            BigDecimal quantity,
            BigDecimal revenue,
            BigDecimal contributionMargin
    ) {
    }

    private record DisposalResult(BigDecimal total, BigDecimal allocated) {
    }

    private record CommercialTerms(
            BigDecimal sellingPrice,
            BigDecimal paymentFee,
            BigDecimal logisticsCost
    ) {
    }

    private record ChannelTerms(
            BigDecimal sellingPrice,
            BigDecimal paymentFee,
            BigDecimal logisticsCost
    ) {
    }

    private static final class CandidatePlan {

        private final Map<Long, AllocationPlan> allocations;
        private final ChannelTerms channelTerms;
        private final Map<Long, BigDecimal> discountRates;

        private CandidatePlan(
                Map<Long, AllocationPlan> allocations,
                ChannelTerms channelTerms,
                Map<Long, BigDecimal> discountRates
        ) {
            this.allocations = Map.copyOf(allocations);
            this.channelTerms = channelTerms;
            this.discountRates = Map.copyOf(discountRates);
        }

        private static CandidatePlan create(StrategyCandidate candidate) {
            Map<Long, AllocationPlanBuilder> builders = new LinkedHashMap<>();
            Map<Long, BigDecimal> discountRates = new LinkedHashMap<>();
            for (StrategyCandidate.Action action : candidate.actions()) {
                boolean movement = action.actionType() == StrategyType.REALLOCATION
                        || action.actionType() == StrategyType.RT_TRANSFER;
                boolean discount = action.actionType() == StrategyType.PRICE_DISCOUNT;
                if (!movement && !discount) {
                    continue;
                }
                if (discount) {
                    Long salesPointId = action.target().salesPointId();
                    if (salesPointId == null) {
                        throw new CandidateSimulationException(
                                "CANDIDATE_DISCOUNT_TARGET_REQUIRED",
                                "Discount action requires a target sales point"
                        );
                    }
                    BigDecimal existing = discountRates.putIfAbsent(
                            salesPointId,
                            action.discountRate()
                    );
                    if (existing != null
                            && existing.compareTo(action.discountRate()) != 0) {
                        throw new CandidateSimulationException(
                                "CANDIDATE_DISCOUNT_CONFLICT",
                                "Discount actions for one sales point must use one rate"
                        );
                    }
                }
                for (StrategyCandidate.LotAllocation allocation
                        : action.lotAllocations()) {
                    AllocationPlanBuilder builder = builders.computeIfAbsent(
                            allocation.inventoryBalanceId(),
                            ignored -> new AllocationPlanBuilder(
                                    allocation.inventoryBalanceId(),
                                    allocation.lotId()
                            )
                    );
                    builder.mergeQuantity(allocation.quantity());
                    if (movement) {
                        builder.setTarget(action.target());
                    }
                    if (discount) {
                        builder.setDiscount(action.strategyPrice());
                    }
                }
            }
            if (builders.isEmpty()) {
                throw new CandidateSimulationException(
                        "CANDIDATE_ALLOCATION_EMPTY",
                        "Candidate has no inventory allocation to simulate"
                );
            }

            Map<Long, AllocationPlan> allocations = new LinkedHashMap<>();
            builders.forEach((key, value) -> allocations.put(key, value.build()));
            ChannelTerms channelTerms = null;
            if (candidate.evidence() instanceof StrategyCandidate.ChannelEvidence evidence) {
                channelTerms = new ChannelTerms(
                        evidence.appliedSellingPrice(),
                        evidence.paymentFee(),
                        evidence.logisticsCost()
                );
            }
            return new CandidatePlan(allocations, channelTerms, discountRates);
        }

        private List<LotState> createLotStates(List<InventoryLot> inventory) {
            List<LotState> result = new ArrayList<>();
            Set<Long> found = new LinkedHashSet<>();
            for (InventoryLot lot : inventory) {
                AllocationPlan allocation = allocations.get(lot.inventoryBalanceId());
                if (allocation != null) {
                    found.add(lot.inventoryBalanceId());
                    if (!Objects.equals(lot.lotId(), allocation.lotId())
                            || allocation.quantity().compareTo(lot.availableQty()) > 0) {
                        throw new CandidateSimulationException(
                                "CANDIDATE_ALLOCATION_INVALID",
                                "Candidate LOT allocation exceeds evaluation inventory"
                        );
                    }
                }
                result.add(new LotState(lot, lot.availableQty(), false));
            }
            if (!found.equals(allocations.keySet())) {
                throw new CandidateSimulationException(
                        "CANDIDATE_ALLOCATION_NOT_FOUND",
                        "Candidate allocation is outside evaluation inventory"
                );
            }
            return result;
        }

        private Map<Long, AllocationPlan> allocations() {
            return allocations;
        }

        private ChannelTerms channelTerms() {
            return channelTerms;
        }

        private BigDecimal discountRate(Long salesPointId) {
            return discountRates.get(salesPointId);
        }
    }

    private static final class AllocationPlanBuilder {

        private final Long inventoryBalanceId;
        private final Long lotId;
        private BigDecimal quantity;
        private Long targetWarehouseId;
        private Long targetSalesPointId;
        private BigDecimal discountPrice;

        private AllocationPlanBuilder(Long inventoryBalanceId, Long lotId) {
            this.inventoryBalanceId = inventoryBalanceId;
            this.lotId = lotId;
        }

        private void mergeQuantity(BigDecimal value) {
            if (quantity != null && quantity.compareTo(value) != 0) {
                throw new CandidateSimulationException(
                        "CANDIDATE_COMPOSITE_ALLOCATION_MISMATCH",
                        "Composite actions must use the same LOT allocation"
                );
            }
            quantity = value;
        }

        private void setTarget(StrategyCandidate.Location target) {
            if ((targetWarehouseId != null
                    && !Objects.equals(targetWarehouseId, target.warehouseId()))
                    || (targetSalesPointId != null
                    && !Objects.equals(targetSalesPointId, target.salesPointId()))) {
                throw new CandidateSimulationException(
                        "CANDIDATE_TARGET_CONFLICT",
                        "Composite movement actions have conflicting targets"
                );
            }
            targetWarehouseId = target.warehouseId();
            targetSalesPointId = target.salesPointId();
        }

        private void setDiscount(BigDecimal value) {
            if (discountPrice != null && discountPrice.compareTo(value) != 0) {
                throw new CandidateSimulationException(
                        "CANDIDATE_DISCOUNT_CONFLICT",
                        "Composite discount actions have conflicting prices"
                );
            }
            discountPrice = value;
        }

        private AllocationPlan build() {
            return new AllocationPlan(
                    inventoryBalanceId,
                    lotId,
                    quantity,
                    targetWarehouseId,
                    targetSalesPointId,
                    discountPrice
            );
        }
    }

    private record AllocationPlan(
            Long inventoryBalanceId,
            Long lotId,
            BigDecimal quantity,
            Long targetWarehouseId,
            Long targetSalesPointId,
            BigDecimal discountPrice
    ) {
    }

    private static final class LotState {

        private static final Comparator<LotState> OUTBOUND_ORDER = Comparator
                .comparing(LotState::expirySortDate)
                .thenComparing(LotState::receivedSortDate)
                .thenComparing(LotState::manufacturedSortDate)
                .thenComparing(lot -> lot.input.inventoryBalanceId())
                .thenComparing(lot -> lot.strategyAllocated ? 0 : 1);

        private final InventoryLot input;
        private final boolean strategyAllocated;
        private BigDecimal remaining;
        private Long warehouseId;
        private Long salesPointId;
        private boolean strategyApplied;
        private BigDecimal discountPrice;

        private LotState(
                InventoryLot input,
                BigDecimal initialQuantity,
                boolean strategyAllocated
        ) {
            this.input = input;
            this.strategyAllocated = strategyAllocated;
            this.remaining = quantity(initialQuantity);
            this.warehouseId = input.warehouseId();
            this.salesPointId = input.effectiveSalesPointId();
        }

        private LocalDate expirySortDate() {
            return input.expiryDate() == null ? LocalDate.MAX : input.expiryDate();
        }

        private LocalDate receivedSortDate() {
            return input.receivedDate() == null ? LocalDate.MAX : input.receivedDate();
        }

        private LocalDate manufacturedSortDate() {
            return input.manufacturedDate() == null
                    ? LocalDate.MAX
                    : input.manufacturedDate();
        }

        private boolean isExpiredAt(LocalDate date) {
            return "EXPIRED".equals(input.lotStatus())
                    || (input.expiryDate() != null
                    && date.isAfter(input.expiryDate()));
        }

        private boolean isBaseSellableAt(LocalDate date) {
            if (isExpiredAt(date)
                    || "SALE_STOPPED".equals(input.lotStatus())
                    || "DEPLETED".equals(input.lotStatus())) {
                return false;
            }
            return input.saleStopDate() == null
                    || date.isBefore(input.saleStopDate());
        }

        private boolean isSellableAt(
                LocalDate date,
                StrategyCandidate candidate
        ) {
            if (remaining.signum() <= 0 || !isBaseSellableAt(date)) {
                return false;
            }
            if (strategyAllocated && !strategyApplied) {
                return false;
            }
            return !strategyAllocated
                    || !candidate.strategyTypes().contains(
                    StrategyType.CHANNEL_EXPANSION)
                    || candidate.endDate() == null
                    || !date.isAfter(candidate.endDate());
        }
    }
}
