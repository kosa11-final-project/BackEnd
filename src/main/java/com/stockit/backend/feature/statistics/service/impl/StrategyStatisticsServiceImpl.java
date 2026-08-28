package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.response.StrategyActionCombinationStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.StrategyLocationPerformanceResponse;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsResponse;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsSummaryResponse;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsTrendPointResponse;
import com.stockit.backend.feature.statistics.mapper.StrategyStatisticsMapper;
import com.stockit.backend.feature.statistics.service.StrategyStatisticsService;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsActionVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsScopeVO;

@Service
@Transactional(readOnly = true)
public class StrategyStatisticsServiceImpl implements StrategyStatisticsService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final BigDecimal GOAL_ACHIEVED_RATE = BigDecimal.valueOf(100);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Map<String, String> ACTION_LABELS = Map.of(
            "REALLOCATION", "재고 이동",
            "RT_TRANSFER", "RT",
            "PRICE_DISCOUNT", "할인",
            "PROMOTION_STOP", "프로모션 중단",
            "CHANNEL_EXPANSION", "채널 확장",
            "CHANNEL_CONCENTRATION", "채널 집중",
            "REPLENISHMENT_REQUEST", "재고 보충 요청",
            "SAFETY_STOCK_ADJUSTMENT", "안전재고 조정"
    );

    private final StrategyStatisticsMapper mapper;

    public StrategyStatisticsServiceImpl(StrategyStatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public StrategyStatisticsResponse getStrategyStatistics(
            LocalDate fromDate,
            LocalDate toDate,
            StatisticsScopeType scopeType,
            String scopeCode
    ) {
        LocalDate resolvedToDate = toDate == null ? LocalDate.now(BUSINESS_ZONE) : toDate;
        LocalDate resolvedFromDate = fromDate == null
                ? resolvedToDate.minusDays(DEFAULT_DAYS - 1L)
                : fromDate;
        validateDateRange(resolvedFromDate, resolvedToDate);

        StatisticsScopeType resolvedScopeType = scopeType == null
                ? StatisticsScopeType.NATIONAL
                : scopeType;
        String resolvedScopeCode = resolveScopeCode(resolvedScopeType, scopeCode);

        List<StrategyStatisticsResultVO> allResults = safe(
                mapper.selectCompletedResults(resolvedFromDate, resolvedToDate)
        );
        if (allResults.isEmpty()) {
            return emptyResponse(resolvedFromDate, resolvedToDate, resolvedScopeType, resolvedScopeCode);
        }

        List<Long> finalSelectionIds = allResults.stream()
                .map(StrategyStatisticsResultVO::getFinalSelectionId)
                .distinct()
                .toList();
        Map<Long, Set<ScopeKey>> scopesBySelection = safe(mapper.selectResultScopes(finalSelectionIds)).stream()
                .collect(Collectors.groupingBy(
                        StrategyStatisticsScopeVO::getFinalSelectionId,
                        Collectors.mapping(
                                row -> new ScopeKey(row.getScopeType(), row.getScopeCode(), row.getScopeName()),
                                Collectors.toSet()
                        )
                ));
        List<StrategyStatisticsResultVO> results = allResults.stream()
                .filter(result -> matchesScope(
                        result,
                        resolvedScopeType,
                        resolvedScopeCode,
                        scopesBySelection
                ))
                .toList();

        if (results.isEmpty()) {
            return emptyResponse(resolvedFromDate, resolvedToDate, resolvedScopeType, resolvedScopeCode);
        }

        List<Long> optionIds = results.stream()
                .map(StrategyStatisticsResultVO::getStrategyOptionId)
                .distinct()
                .toList();
        Map<Long, Set<String>> actionsByOption = safe(mapper.selectActionTypes(optionIds)).stream()
                .collect(Collectors.groupingBy(
                        StrategyStatisticsActionVO::getStrategyOptionId,
                        Collectors.mapping(StrategyStatisticsActionVO::getActionType, Collectors.toCollection(TreeSet::new))
                ));

        Aggregate total = aggregate(results);
        Map<LocalDate, List<StrategyStatisticsResultVO>> resultsByDate = results.stream()
                .collect(Collectors.groupingBy(
                        StrategyStatisticsResultVO::getExecutionEndDate,
                        TreeMap::new,
                        Collectors.toList()
                ));
        List<StrategyStatisticsTrendPointResponse> dailyTrend = resultsByDate.entrySet().stream()
                .map(entry -> toTrendPoint(entry.getKey(), aggregate(entry.getValue())))
                .toList();
        List<StrategyActionCombinationStatisticsResponse> combinations = combinationBreakdown(
                results,
                actionsByOption
        );
        List<StrategyLocationPerformanceResponse> locationPerformance = locationPerformance(
                allResults,
                scopesBySelection,
                resolvedScopeType
        );
        List<StrategyLocationPerformanceResponse> scopePerformance = scopePerformance(
                allResults,
                scopesBySelection
        );

        return new StrategyStatisticsResponse(
                resolvedFromDate,
                resolvedToDate,
                resolvedScopeType.name(),
                resolvedScopeCode,
                total.toSummary(),
                locationPerformance,
                scopePerformance,
                dailyTrend,
                combinations
        );
    }

    private static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (days <= 0 || days > MAX_DAYS) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private static String resolveScopeCode(StatisticsScopeType scopeType, String scopeCode) {
        if (scopeCode != null && !scopeCode.isBlank()) {
            return scopeCode.trim();
        }
        return scopeType == StatisticsScopeType.UNASSIGNED ? "UNASSIGNED" : "ALL";
    }

    private static boolean matchesScope(
            StrategyStatisticsResultVO result,
            StatisticsScopeType scopeType,
            String scopeCode,
            Map<Long, Set<ScopeKey>> scopesBySelection
    ) {
        if (scopeType == StatisticsScopeType.NATIONAL) {
            return true;
        }
        Set<ScopeKey> scopes = scopesBySelection.getOrDefault(result.getFinalSelectionId(), Set.of());
        return scopes.stream().anyMatch(scope -> scope.matches(scopeType.name(), scopeCode));
    }

    private static Aggregate aggregate(List<StrategyStatisticsResultVO> results) {
        Aggregate aggregate = new Aggregate();
        results.forEach(aggregate::add);
        return aggregate;
    }

    private static StrategyStatisticsTrendPointResponse toTrendPoint(LocalDate date, Aggregate aggregate) {
        return new StrategyStatisticsTrendPointResponse(
                date,
                aggregate.completedCount,
                aggregate.goalAchievedCount,
                aggregate.averageAchievementRate(),
                aggregate.baselineRiskStockQty,
                aggregate.riskStockReductionQty,
                aggregate.avoidedDisposalQty,
                aggregate.estimatedLossSavingsAmount
        );
    }

    private static List<StrategyActionCombinationStatisticsResponse> combinationBreakdown(
            List<StrategyStatisticsResultVO> results,
            Map<Long, Set<String>> actionsByOption
    ) {
        Map<String, List<StrategyStatisticsResultVO>> grouped = new TreeMap<>();
        Map<String, String> labels = new HashMap<>();
        for (StrategyStatisticsResultVO result : results) {
            Set<String> actions = actionsByOption.getOrDefault(result.getStrategyOptionId(), Set.of());
            String code = actions.isEmpty() ? "NO_ACTION" : String.join("+", actions);
            String label = actions.isEmpty()
                    ? "액션 미등록"
                    : actions.stream().map(action -> ACTION_LABELS.getOrDefault(action, action))
                            .collect(Collectors.joining(" + "));
            grouped.computeIfAbsent(code, ignored -> new ArrayList<>()).add(result);
            labels.put(code, label);
        }

        return grouped.entrySet().stream()
                .map(entry -> {
                    Aggregate aggregate = aggregate(entry.getValue());
                    return new StrategyActionCombinationStatisticsResponse(
                            entry.getKey(),
                            labels.get(entry.getKey()),
                            aggregate.completedCount,
                            aggregate.averageAchievementRate(),
                            aggregate.riskStockReductionRate(),
                            aggregate.avoidedDisposalQty,
                            aggregate.estimatedLossSavingsAmount
                    );
                })
                .sorted(Comparator.comparingLong(StrategyActionCombinationStatisticsResponse::completedCount).reversed()
                        .thenComparing(StrategyActionCombinationStatisticsResponse::code))
                .toList();
    }

    private static List<StrategyLocationPerformanceResponse> locationPerformance(
            List<StrategyStatisticsResultVO> allResults,
            Map<Long, Set<ScopeKey>> scopesBySelection,
            StatisticsScopeType requestedScopeType
    ) {
        if (requestedScopeType == StatisticsScopeType.UNASSIGNED) {
            return List.of();
        }

        Set<String> includedTypes = requestedScopeType == StatisticsScopeType.NATIONAL
                ? Set.of(
                        StatisticsScopeType.WAREHOUSE.name(),
                        StatisticsScopeType.OFFLINE_STORE.name(),
                        StatisticsScopeType.ONLINE_STORE.name()
                )
                : Set.of(requestedScopeType.name());
        Map<ScopeKey, List<StrategyStatisticsResultVO>> grouped = new HashMap<>();

        for (StrategyStatisticsResultVO result : allResults) {
            for (ScopeKey scope : scopesBySelection.getOrDefault(result.getFinalSelectionId(), Set.of())) {
                if (includedTypes.contains(scope.type())) {
                    grouped.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(result);
                }
            }
        }

        return grouped.entrySet().stream()
                .map(entry -> toPerformance(
                        entry.getKey().code(),
                        entry.getKey().code(),
                        displayName(entry.getKey()),
                        entry.getKey().type(),
                        aggregate(entry.getValue())
                ))
                .sorted(Comparator.comparing(StrategyLocationPerformanceResponse::riskStockReductionRate).reversed()
                        .thenComparing(StrategyLocationPerformanceResponse::name))
                .toList();
    }

    private static List<StrategyLocationPerformanceResponse> scopePerformance(
            List<StrategyStatisticsResultVO> allResults,
            Map<Long, Set<ScopeKey>> scopesBySelection
    ) {
        return List.of(
                scopePerformance(
                        StatisticsScopeType.WAREHOUSE,
                        "물류센터",
                        allResults,
                        scopesBySelection
                ),
                scopePerformance(
                        StatisticsScopeType.OFFLINE_STORE,
                        "오프라인 매장",
                        allResults,
                        scopesBySelection
                ),
                scopePerformance(
                        StatisticsScopeType.ONLINE_STORE,
                        "온라인 판매처",
                        allResults,
                        scopesBySelection
                )
        ).stream()
                .sorted(Comparator.comparing(StrategyLocationPerformanceResponse::riskStockReductionRate).reversed())
                .toList();
    }

    private static StrategyLocationPerformanceResponse scopePerformance(
            StatisticsScopeType scopeType,
            String name,
            List<StrategyStatisticsResultVO> allResults,
            Map<Long, Set<ScopeKey>> scopesBySelection
    ) {
        List<StrategyStatisticsResultVO> scopedResults = allResults.stream()
                .filter(result -> scopesBySelection.getOrDefault(result.getFinalSelectionId(), Set.of()).stream()
                        .anyMatch(scope -> scopeType.name().equals(scope.type())))
                .toList();
        return toPerformance(
                scopeType.name(),
                scopeType.name(),
                name,
                scopeType.name(),
                aggregate(scopedResults)
        );
    }

    private static StrategyLocationPerformanceResponse toPerformance(
            String id,
            String code,
            String name,
            String scopeType,
            Aggregate aggregate
    ) {
        return new StrategyLocationPerformanceResponse(
                id,
                code,
                name,
                scopeType,
                aggregate.completedCount,
                aggregate.goalAchievedStrategyRate(),
                aggregate.baselineRiskStockQty,
                aggregate.riskStockReductionQty,
                aggregate.riskStockReductionRate(),
                aggregate.estimatedLossSavingsAmount
        );
    }

    private static String displayName(ScopeKey scope) {
        return scope.name() == null || scope.name().isBlank() ? scope.code() : scope.name();
    }

    private static StrategyStatisticsResponse emptyResponse(
            LocalDate fromDate,
            LocalDate toDate,
            StatisticsScopeType scopeType,
            String scopeCode
    ) {
        return new StrategyStatisticsResponse(
                fromDate,
                toDate,
                scopeType.name(),
                scopeCode,
                new Aggregate().toSummary(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ScopeKey(String type, String code, String name) {
        private boolean matches(String requestedType, String requestedCode) {
            if (type == null || !type.equals(requestedType)) {
                return false;
            }
            return "ALL".equals(requestedCode)
                    || (code != null && code.equalsIgnoreCase(requestedCode));
        }
    }

    private static final class Aggregate {
        private long completedCount;
        private long goalAchievedCount;
        private BigDecimal achievementRateTotal = BigDecimal.ZERO;
        private BigDecimal baselineRiskStockQty = BigDecimal.ZERO;
        private BigDecimal endRiskStockQty = BigDecimal.ZERO;
        private BigDecimal riskStockReductionQty = BigDecimal.ZERO;
        private BigDecimal baselineExpectedDisposalQty = BigDecimal.ZERO;
        private BigDecimal endExpectedDisposalQty = BigDecimal.ZERO;
        private BigDecimal avoidedDisposalQty = BigDecimal.ZERO;
        private BigDecimal baselineEstimatedLossAmount = BigDecimal.ZERO;
        private BigDecimal endEstimatedLossAmount = BigDecimal.ZERO;
        private BigDecimal estimatedLossSavingsAmount = BigDecimal.ZERO;

        private void add(StrategyStatisticsResultVO result) {
            completedCount++;
            BigDecimal achievementRate = zero(result.getAchievementRate());
            if (achievementRate.compareTo(GOAL_ACHIEVED_RATE) >= 0) {
                goalAchievedCount++;
            }
            achievementRateTotal = achievementRateTotal.add(achievementRate);

            BigDecimal startRisk = zero(result.getStartRiskStockQty());
            BigDecimal endRisk = zero(result.getEndRiskStockQty());
            baselineRiskStockQty = baselineRiskStockQty.add(startRisk);
            endRiskStockQty = endRiskStockQty.add(endRisk);
            riskStockReductionQty = riskStockReductionQty.add(startRisk.subtract(endRisk));

            BigDecimal startDisposal = zero(result.getStartExpectedDisposalQty());
            BigDecimal endDisposal = zero(result.getEndExpectedDisposalQty());
            BigDecimal unitCost = zero(result.getStartUnitCost());
            baselineExpectedDisposalQty = baselineExpectedDisposalQty.add(startDisposal);
            endExpectedDisposalQty = endExpectedDisposalQty.add(endDisposal);
            avoidedDisposalQty = avoidedDisposalQty.add(startDisposal.subtract(endDisposal));
            baselineEstimatedLossAmount = baselineEstimatedLossAmount.add(startDisposal.multiply(unitCost));
            endEstimatedLossAmount = endEstimatedLossAmount.add(endDisposal.multiply(unitCost));
            estimatedLossSavingsAmount = estimatedLossSavingsAmount.add(
                    zero(result.getEstimatedLossSavingsAmount())
            );
        }

        private StrategyStatisticsSummaryResponse toSummary() {
            return new StrategyStatisticsSummaryResponse(
                    completedCount,
                    goalAchievedCount,
                    percentage(goalAchievedCount, completedCount),
                    averageAchievementRate(),
                    baselineRiskStockQty,
                    endRiskStockQty,
                    riskStockReductionQty,
                    riskStockReductionRate(),
                    baselineExpectedDisposalQty,
                    endExpectedDisposalQty,
                    avoidedDisposalQty,
                    baselineEstimatedLossAmount,
                    endEstimatedLossAmount,
                    estimatedLossSavingsAmount
            );
        }

        private BigDecimal goalAchievedStrategyRate() {
            return percentage(goalAchievedCount, completedCount);
        }

        private BigDecimal averageAchievementRate() {
            return completedCount == 0
                    ? BigDecimal.ZERO
                    : achievementRateTotal.divide(BigDecimal.valueOf(completedCount), 4, RoundingMode.HALF_UP);
        }

        private BigDecimal riskStockReductionRate() {
            return baselineRiskStockQty.signum() == 0
                    ? BigDecimal.ZERO
                    : riskStockReductionQty.multiply(BigDecimal.valueOf(100))
                            .divide(baselineRiskStockQty, 4, RoundingMode.HALF_UP);
        }

        private static BigDecimal percentage(long numerator, long denominator) {
            return denominator == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
        }

        private static BigDecimal zero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }
}
