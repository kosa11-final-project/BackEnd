package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusion;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateGenerationResult;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.MovementCandidatePlan;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidateIdGenerator;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyPeriodCandidate;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.DiscountRateCandidatePolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.DiscountRateCandidatePolicy.DiscountOption;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.MovementLotAllocationPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.SourceInventoryCapacityPolicy;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodCandidatePolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.Price;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;
import com.stockit.backend.feature.strategy.calculation.policy.DiscountDemandPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/**
 * 현재 판매처의 최저 판매가와 예상 잔여 재고를 지키는 5% 단위 할인 후보 계산기
 */
@Component
public class PriceDiscountCandidateCalculator implements StrategyCandidateCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(
            CalculationPrecisionPolicy.MONEY_SCALE
    );

    private final DiscountRateCandidatePolicy discountRatePolicy;
    private final StrategyPeriodCandidatePolicy periodPolicy;
    private final SourceInventoryCapacityPolicy sourceCapacityPolicy;
    private final MovementLotAllocationPolicy allocationPolicy;
    private final StrategyCandidateIdGenerator idGenerator;
    private final DiscountDemandPolicy discountDemandPolicy;

    public PriceDiscountCandidateCalculator(
            DiscountRateCandidatePolicy discountRatePolicy,
            StrategyPeriodCandidatePolicy periodPolicy,
            SourceInventoryCapacityPolicy sourceCapacityPolicy,
            MovementLotAllocationPolicy allocationPolicy,
            StrategyCandidateIdGenerator idGenerator,
            DiscountDemandPolicy discountDemandPolicy
    ) {
        this.discountRatePolicy = discountRatePolicy;
        this.periodPolicy = periodPolicy;
        this.sourceCapacityPolicy = sourceCapacityPolicy;
        this.allocationPolicy = allocationPolicy;
        this.idGenerator = idGenerator;
        this.discountDemandPolicy = discountDemandPolicy;
    }

    @Override
    public StrategyType supportedType() {
        return StrategyType.PRICE_DISCOUNT;
    }

    /**
     * 기간·할인율·적용 수량 조합별 가격 할인 후보를 생성한다
     *
     * <p>각 기간의 시작 시점에 남을 것으로 예상되는 수량을 다시 계산해
     * 미래 재고를 미리 예약하는 후보가 생성되지 않도록 한다</p>
     */
    @Override
    public CandidateGenerationResult generate(
            StrategyCalculationContext context,
            int strategyPriority
    ) {
        Long sourceId = context.sourceSalesPointId();
        if (sourceId == null) {
            return excluded(
                    null,
                    CandidateExclusionReason.SOURCE_SALES_POINT_REQUIRED,
                    "Public unassigned inventory cannot be discounted before allocation"
            );
        }
        SalesPoint source = context.salesPoints().get(sourceId);
        if (source == null || !source.hasCompletePrice()) {
            return excluded(
                    sourceId,
                    CandidateExclusionReason.SOURCE_PRICE_INCOMPLETE,
                    "Source sales point price or variable cost is incomplete"
            );
        }
        Price sourcePrice = source.price();
        if (sourcePrice.minimumSellingPrice() == null) {
            return excluded(
                    sourceId,
                    CandidateExclusionReason.MINIMUM_SELLING_PRICE_MISSING,
                    "Minimum selling price is required for discount calculation"
            );
        }
        List<DiscountOption> discountOptions = discountRatePolicy.generate(
                sourcePrice,
                source
        );
        if (discountOptions.isEmpty()) {
            return excluded(
                    sourceId,
                    CandidateExclusionReason.DISCOUNT_RATE_NOT_AVAILABLE,
                    "No 5 percent discount can satisfy the minimum selling price"
            );
        }

        SourceInventoryCapacityPolicy.Capacity currentSourceCapacity =
                sourceCapacityPolicy.resolve(context, context.evaluationInventory());
        if (currentSourceCapacity.total().signum() == 0) {
            return excluded(
                    sourceId,
                    CandidateExclusionReason.SOURCE_STOCK_INSUFFICIENT,
                    "No sellable source inventory is available to discount"
            );
        }

        List<StrategyCandidate> candidates = new ArrayList<>();
        Map<LocalDate, StrategyCalculationContext> projectedContexts =
                new LinkedHashMap<>();
        for (StrategyPeriodCandidate period : periodPolicy.generate(context, sourceId)) {
            // 같은 시작일의 기간 후보는 동일한 미래 재고 스냅샷을 재사용
            StrategyCalculationContext projectedContext = projectedContexts.computeIfAbsent(
                    period.startDate(),
                    startDate -> projectedContext(context, startDate)
            );
            SourceInventoryCapacityPolicy.Capacity sourceCapacity =
                    sourceCapacityPolicy.resolve(
                            projectedContext,
                            projectedContext.evaluationInventory(),
                            period.startDate()
                    );
            if (sourceCapacity.total().signum() == 0) {
                continue;
            }
            Map<LocalDate, BigDecimal> baselinePeriodDemand = periodDemand(
                    source.dailyForecast(),
                    period
            );
            BigDecimal baselineDemand = sum(baselinePeriodDemand.values());
            for (DiscountOption discount : discountOptions) {
                Map<LocalDate, BigDecimal> discountedDemand = discountedDemand(
                        baselinePeriodDemand,
                        source,
                        discount.discountRate()
                );
                BigDecimal discountedDemandTotal = sum(discountedDemand.values());
                MovementCandidatePlan maximumPlan = allocationPolicy.plan(
                        projectedContext.evaluationInventory(),
                        sourceCapacity.byLocation(),
                        discountedDemand,
                        sourceCapacity.total().min(discountedDemandTotal)
                );
                BigDecimal maxExecutableQuantity = CalculationPrecisionPolicy
                        .executableQuantity(maximumPlan.plannedQuantity());
                if (maxExecutableQuantity.signum() == 0) {
                    continue;
                }
                List<QuantityTier> quantityTiers = planQuantityTiers(
                        projectedContext,
                        sourceCapacity,
                        discountedDemand,
                        maximumPlan,
                        maxExecutableQuantity
                );
                addCandidates(
                        candidates,
                        strategyPriority,
                        sourceId,
                        sourcePrice,
                        sourceCapacity,
                        period,
                        baselineDemand,
                        quantityTiers,
                        maxExecutableQuantity,
                        discount
                );
            }
        }
        if (candidates.isEmpty()) {
            return excluded(
                    sourceId,
                    CandidateExclusionReason.LOT_NOT_SELLABLE_IN_PERIOD,
                    "Selected LOT cannot be sold in any generated discount period"
            );
        }
        return new CandidateGenerationResult(candidates, List.of());
    }

    private Map<LocalDate, BigDecimal> discountedDemand(
            Map<LocalDate, BigDecimal> baselineDemand,
            SalesPoint salesPoint,
            BigDecimal discountRate
    ) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        baselineDemand.forEach((date, demand) -> result.put(
                date,
                discountDemandPolicy.apply(demand, salesPoint, discountRate)
        ));
        return result;
    }

    private StrategyCalculationContext projectedContext(
            StrategyCalculationContext context,
            LocalDate startDate
    ) {
        SourceInventoryCapacityPolicy.Projection projection =
                sourceCapacityPolicy.projectAt(context, startDate);
        return context.withInventory(
                projection.evaluationInventory(),
                projection.referenceInventory()
        );
    }

    private List<QuantityTier> planQuantityTiers(
            StrategyCalculationContext context,
            SourceInventoryCapacityPolicy.Capacity sourceCapacity,
            Map<LocalDate, BigDecimal> periodDemand,
            MovementCandidatePlan maximumPlan,
            BigDecimal maxExecutableQuantity
    ) {
        List<QuantityTier> tiers = new ArrayList<>();
        Set<BigDecimal> generatedQuantities = new LinkedHashSet<>();
        for (int percentage = 10; percentage <= 100; percentage += 10) {
            BigDecimal requested = CalculationPrecisionPolicy.executableQuantity(
                    maxExecutableQuantity
                    .multiply(BigDecimal.valueOf(percentage))
                    .divide(ONE_HUNDRED, CalculationPrecisionPolicy.QUANTITY_SCALE,
                            RoundingMode.DOWN));
            if (requested.signum() == 0 || !generatedQuantities.add(requested)) {
                continue;
            }
            MovementCandidatePlan plan = requested.compareTo(
                    maximumPlan.plannedQuantity()
            ) == 0
                    ? maximumPlan
                    : allocationPolicy.plan(
                            context.evaluationInventory(),
                            sourceCapacity.byLocation(),
                            periodDemand,
                            requested
                    );
            if (plan.plannedQuantity().compareTo(requested) != 0) {
                continue;
            }
            tiers.add(new QuantityTier(percentage, plan));
        }
        return List.copyOf(tiers);
    }

    private void addCandidates(
            List<StrategyCandidate> candidates,
            int strategyPriority,
            Long sourceId,
            Price sourcePrice,
            SourceInventoryCapacityPolicy.Capacity sourceCapacity,
            StrategyPeriodCandidate period,
            BigDecimal baselineDemand,
            List<QuantityTier> quantityTiers,
            BigDecimal maxExecutableQuantity,
            DiscountOption discount
    ) {
        for (QuantityTier quantityTier : quantityTiers) {
            MovementCandidatePlan plan = quantityTier.plan();
            List<StrategyCandidate.Action> actions = toActions(
                    plan,
                    discount
            );
            String candidateId = idGenerator.generate(
                    List.of(StrategyType.PRICE_DISCOUNT),
                    period.startDate(),
                    period.endDate(),
                    actions
            );
            candidates.add(new StrategyCandidate(
                    candidateId,
                    List.of(StrategyType.PRICE_DISCOUNT),
                    period.startDate(),
                    period.endDate(),
                    actions,
                    List.of(),
                    new StrategyCandidate.Preference(
                            strategyPriority,
                            1,
                            quantityTier.percentage()
                    ),
                    new StrategyCandidate.DiscountEvidence(
                            maxExecutableQuantity,
                            sourceCapacity.total(),
                            baselineDemand,
                            maxExecutableQuantity,
                            sourcePrice.actualPrice(),
                            sourcePrice.minimumSellingPrice()
                    )
            ));
        }
    }

    private static List<StrategyCandidate.Action> toActions(
            MovementCandidatePlan plan,
            DiscountOption discount
    ) {
        Map<ActionKey, List<MovementCandidatePlan.Allocation>> byLocation =
                plan.allocations().stream().collect(Collectors.groupingBy(
                        allocation -> new ActionKey(
                                allocation.sourceWarehouseId(),
                                allocation.sourceSalesPointId()
                        ),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<StrategyCandidate.Action> actions = new ArrayList<>();
        for (Map.Entry<ActionKey, List<MovementCandidatePlan.Allocation>> entry
                : byLocation.entrySet()) {
            List<StrategyCandidate.LotAllocation> allocations = new ArrayList<>();
            int priority = 1;
            for (MovementCandidatePlan.Allocation allocation : entry.getValue()) {
                allocations.add(new StrategyCandidate.LotAllocation(
                        allocation.inventoryBalanceId(),
                        allocation.lotId(),
                        allocation.quantity(),
                        priority++
                ));
            }
            BigDecimal actionQuantity = sum(entry.getValue().stream()
                    .map(MovementCandidatePlan.Allocation::quantity)
                    .toList());
            StrategyCandidate.Location location = new StrategyCandidate.Location(
                    entry.getKey().warehouseId(),
                    entry.getKey().salesPointId()
            );
            actions.add(new StrategyCandidate.Action(
                    StrategyType.PRICE_DISCOUNT,
                    location,
                    location,
                    actionQuantity,
                    ZERO_MONEY,
                    discount.strategyPrice(),
                    discount.discountRate(),
                    allocations
            ));
        }
        actions.sort(Comparator
                .comparing(
                        (StrategyCandidate.Action action) ->
                                action.source().warehouseId(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        action -> action.source().salesPointId(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));
        return actions;
    }

    private static Map<LocalDate, BigDecimal> periodDemand(
            Map<LocalDate, BigDecimal> fullForecast,
            StrategyPeriodCandidate period
    ) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (LocalDate date = period.startDate();
                !date.isAfter(period.endDate());
                date = date.plusDays(1)) {
            BigDecimal demand = fullForecast.get(date);
            if (demand == null || demand.signum() < 0) {
                throw new StrategyCalculationException(
                        "CALCULATION_FORECAST_INVALID",
                        "Daily forecast is missing or negative: " + date
                );
            }
            result.put(date, demand);
        }
        return result;
    }

    private static CandidateGenerationResult excluded(
            Long sourceId,
            CandidateExclusionReason reason,
            String message
    ) {
        return new CandidateGenerationResult(
                List.of(),
                List.of(new CandidateExclusion(
                        StrategyType.PRICE_DISCOUNT,
                        sourceId,
                        reason,
                        message
                ))
        );
    }

    private static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            result = result.add(value);
        }
        return quantity(result);
    }

    private static BigDecimal quantity(BigDecimal value) {
        return CalculationPrecisionPolicy.quantity(value);
    }

    private record QuantityTier(
            int percentage,
            MovementCandidatePlan plan
    ) {
    }

    private record ActionKey(Long warehouseId, Long salesPointId) {
    }
}
