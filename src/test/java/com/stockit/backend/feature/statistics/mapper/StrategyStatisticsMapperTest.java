package com.stockit.backend.feature.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.statistics.vo.StrategyStatisticsActionVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyStatisticsScopeVO;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionStartCandidateVO;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionDueResultVO;

@SpringBootTest
@ActiveProfiles("test")
@Sql(
        scripts = "/statistics/strategy_statistics_mapper_test_schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class StrategyStatisticsMapperTest {

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:strategy-statistics;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private StrategyStatisticsMapper mapper;

    @Test
    void readsCompletedResultActionsAndEveryInvolvedScopeOnce() {
        List<StrategyStatisticsResultVO> results = mapper.selectCompletedResults(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );
        List<StrategyStatisticsActionVO> actions = mapper.selectActionTypes(List.of(1001L));
        List<StrategyStatisticsScopeVO> scopes = mapper.selectResultScopes(List.of(5001L));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getExecutionEndDate()).isEqualTo(LocalDate.of(2026, 8, 10));
            assertThat(result.getAchievementRate()).isEqualByComparingTo("120");
            assertThat(result.getStartRiskStockQty()).isEqualByComparingTo("100");
        });
        assertThat(actions).extracting(StrategyStatisticsActionVO::getActionType)
                .containsExactly("PRICE_DISCOUNT", "REALLOCATION");
        assertThat(scopes).extracting(
                StrategyStatisticsScopeVO::getScopeType,
                StrategyStatisticsScopeVO::getScopeCode
        ).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("WAREHOUSE", "WH-1"),
                org.assertj.core.groups.Tuple.tuple("OFFLINE_STORE", "STORE-1")
        );
    }

    @Test
    void capturesTheSelectedActionPeriodAndStartBaselineOnce() {
        List<StrategyExecutionStartCandidateVO> candidates = mapper.selectExecutionStartCandidates(
                LocalDate.of(2026, 8, 16)
        );

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.getFinalSelectionId()).isEqualTo(5002L);
            assertThat(candidate.getPlannedStartDate()).isEqualTo(LocalDate.of(2026, 8, 15));
            assertThat(candidate.getPlannedEndDate()).isEqualTo(LocalDate.of(2026, 8, 22));
            assertThat(candidate.getGoalTargetValue()).isEqualByComparingTo("80");
            assertThat(candidate.getStartRiskStockQty()).isEqualByComparingTo("100");
            assertThat(candidate.getStartExpectedDisposalQty()).isEqualByComparingTo("40");
        });

        assertThat(mapper.insertExecutionStartResult(candidates.get(0))).isEqualTo(1);
        assertThat(mapper.insertExecutionStartResult(candidates.get(0))).isZero();
    }

    @Test
    void finalizesOnlyAfterPostEndSyncAndCountsSalesInsteadOfMovedInventory() {
        List<StrategyExecutionDueResultVO> dueResults = mapper.selectDueExecutionResults(
                LocalDate.of(2026, 8, 12)
        );

        assertThat(dueResults).singleElement().satisfies(result -> {
            assertThat(result.getFinalSelectionId()).isEqualTo(5003L);
            assertThat(result.getFinalizedSyncRunId()).isEqualTo(7001L);
            assertThat(result.getGoalActualValue()).isEqualByComparingTo("50");
            assertThat(result.getEndRiskStockQty()).isEqualByComparingTo("40");
            assertThat(result.getEndExpectedDisposalQty()).isEqualByComparingTo("30");
        });

        StrategyExecutionDueResultVO result = dueResults.get(0);
        assertThat(mapper.completeExecutionResult(
                result,
                new java.math.BigDecimal("62.500000"),
                java.math.BigDecimal.ZERO
        )).isEqualTo(1);
        assertThat(mapper.completeExecutionResult(
                result,
                new java.math.BigDecimal("62.500000"),
                java.math.BigDecimal.ZERO
        )).isZero();
    }
}
