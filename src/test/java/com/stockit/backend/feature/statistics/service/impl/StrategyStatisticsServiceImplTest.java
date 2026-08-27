package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsResponse;
import com.stockit.backend.feature.statistics.mapper.StrategyStatisticsMapper;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsActionVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsScopeVO;

@ExtendWith(MockitoExtension.class)
class StrategyStatisticsServiceImplTest {

    private static final LocalDate FROM_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 8, 31);

    @Mock
    private StrategyStatisticsMapper mapper;

    private StrategyStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyStatisticsServiceImpl(mapper);
    }

    @Test
    void aggregatesOnlyCompletedResultsAndKeepsActualActionCombinations() {
        StrategyStatisticsResultVO first = result(1, 101, LocalDate.of(2026, 8, 10), 120, 100, 40, 30, 10, 2000);
        StrategyStatisticsResultVO second = result(2, 102, LocalDate.of(2026, 8, 11), 80, 200, 210, 40, 50, -500);
        when(mapper.selectCompletedResults(FROM_DATE, TO_DATE)).thenReturn(List.of(first, second));
        when(mapper.selectResultScopes(List.of(1L, 2L))).thenReturn(List.of());
        when(mapper.selectActionTypes(List.of(101L, 102L))).thenReturn(List.of(
                action(101, "PRICE_DISCOUNT"),
                action(101, "REALLOCATION"),
                action(102, "PRICE_DISCOUNT")
        ));

        StrategyStatisticsResponse response = service.getStrategyStatistics(
                FROM_DATE,
                TO_DATE,
                StatisticsScopeType.NATIONAL,
                "ALL"
        );

        assertThat(response.summary()).satisfies(summary -> {
            assertThat(summary.completedCount()).isEqualTo(2);
            assertThat(summary.goalAchievedCount()).isEqualTo(1);
            assertThat(summary.goalAchievedStrategyRate()).isEqualByComparingTo("50.0000");
            assertThat(summary.averageAchievementRate()).isEqualByComparingTo("100.0000");
            assertThat(summary.baselineRiskStockQty()).isEqualByComparingTo("300");
            assertThat(summary.endRiskStockQty()).isEqualByComparingTo("250");
            assertThat(summary.riskStockReductionQty()).isEqualByComparingTo("50");
            assertThat(summary.riskStockReductionRate()).isEqualByComparingTo("16.6667");
            assertThat(summary.baselineExpectedDisposalQty()).isEqualByComparingTo("70");
            assertThat(summary.endExpectedDisposalQty()).isEqualByComparingTo("60");
            assertThat(summary.avoidedDisposalQty()).isEqualByComparingTo("10");
            assertThat(summary.baselineEstimatedLossAmount()).isEqualByComparingTo("7000");
            assertThat(summary.endEstimatedLossAmount()).isEqualByComparingTo("6000");
            assertThat(summary.estimatedLossSavingsAmount()).isEqualByComparingTo("1500");
        });
        assertThat(response.dailyTrend()).hasSize(2);
        assertThat(response.actionCombinationBreakdown()).extracting("code")
                .containsExactly("PRICE_DISCOUNT", "PRICE_DISCOUNT+REALLOCATION");
    }

    @Test
    void filtersAResultByItsWarehouseInvolvementWithoutDoubleCounting() {
        StrategyStatisticsResultVO first = result(1, 101, LocalDate.of(2026, 8, 10), 100, 100, 40, 30, 10, 2000);
        StrategyStatisticsResultVO second = result(2, 102, LocalDate.of(2026, 8, 11), 100, 50, 20, 10, 5, 500);
        when(mapper.selectCompletedResults(FROM_DATE, TO_DATE)).thenReturn(List.of(first, second));
        when(mapper.selectResultScopes(List.of(1L, 2L))).thenReturn(List.of(
                scope(1, "WAREHOUSE", "WH-1"),
                scope(1, "WAREHOUSE", "WH-2"),
                scope(1, "OFFLINE_STORE", "STORE-1"),
                scope(2, "ONLINE_STORE", "ONLINE-1")
        ));
        when(mapper.selectActionTypes(List.of(101L))).thenReturn(List.of(action(101, "REALLOCATION")));

        StrategyStatisticsResponse response = service.getStrategyStatistics(
                FROM_DATE,
                TO_DATE,
                StatisticsScopeType.WAREHOUSE,
                "WH-1"
        );

        assertThat(response.summary().completedCount()).isEqualTo(1);
        assertThat(response.summary().riskStockReductionQty()).isEqualByComparingTo("60");
        assertThat(response.locationPerformance()).extracting("code")
                .containsExactlyInAnyOrder("WH-1", "WH-2");
        assertThat(response.locationPerformance()).allSatisfy(location ->
                assertThat(location.completedCount()).isEqualTo(1));
        assertThat(response.scopePerformance())
                .filteredOn(location -> "WAREHOUSE".equals(location.scopeType()))
                .singleElement()
                .satisfies(location -> assertThat(location.completedCount()).isEqualTo(1));
    }

    @Test
    void rejectsRangesLongerThanOneYear() {
        assertThatThrownBy(() -> service.getStrategyStatistics(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2),
                StatisticsScopeType.NATIONAL,
                "ALL"
        )).isInstanceOf(AppException.class);
    }

    private static StrategyStatisticsResultVO result(
            long selectionId,
            long optionId,
            LocalDate endDate,
            long achievementRate,
            long startRisk,
            long endRisk,
            long startDisposal,
            long endDisposal,
            long savings
    ) {
        StrategyStatisticsResultVO value = new StrategyStatisticsResultVO();
        value.setFinalSelectionId(selectionId);
        value.setStrategyCaseId(selectionId + 1000);
        value.setStrategyOptionId(optionId);
        value.setExecutionEndDate(endDate);
        value.setAchievementRate(BigDecimal.valueOf(achievementRate));
        value.setStartRiskStockQty(BigDecimal.valueOf(startRisk));
        value.setEndRiskStockQty(BigDecimal.valueOf(endRisk));
        value.setStartExpectedDisposalQty(BigDecimal.valueOf(startDisposal));
        value.setEndExpectedDisposalQty(BigDecimal.valueOf(endDisposal));
        value.setStartUnitCost(BigDecimal.valueOf(100));
        value.setEstimatedLossSavingsAmount(BigDecimal.valueOf(savings));
        return value;
    }

    private static StrategyStatisticsActionVO action(long optionId, String actionType) {
        StrategyStatisticsActionVO value = new StrategyStatisticsActionVO();
        value.setStrategyOptionId(optionId);
        value.setActionType(actionType);
        return value;
    }

    private static StrategyStatisticsScopeVO scope(long selectionId, String scopeType, String scopeCode) {
        StrategyStatisticsScopeVO value = new StrategyStatisticsScopeVO();
        value.setFinalSelectionId(selectionId);
        value.setScopeType(scopeType);
        value.setScopeCode(scopeCode);
        value.setScopeName(scopeCode);
        return value;
    }
}
