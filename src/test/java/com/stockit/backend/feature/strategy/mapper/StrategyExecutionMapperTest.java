package com.stockit.backend.feature.strategy.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.strategy.vo.StrategyExecutionActionVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionBaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionDailySalesVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionInventoryVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionPerformanceVO;

@SpringBootTest
@ActiveProfiles("test")
@Sql(
        scripts = "/strategy/strategy-execution-mapper-test-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class StrategyExecutionMapperTest {

    @DynamicPropertySource
    static void useIsolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:strategy-execution;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        );
    }

    @Autowired
    private StrategyExecutionMapper mapper;

    @Test
    void readsOnlyFinalSelectionsAndSupportedActionsInBulk() {
        List<StrategyExecutionBaseVO> bases = mapper.selectFinalStrategyExecutions();
        List<StrategyExecutionActionVO> actions = mapper.selectSupportedActions(List.of(1001L));

        assertThat(bases).singleElement().satisfies(base -> {
            assertThat(base.getStrategyCaseId()).isEqualTo(101L);
            assertThat(base.getImageUrl()).isEqualTo("https://example.com/image.jpg");
            assertThat(base.getLastSyncedAt()).isNotNull();
        });
        assertThat(actions).singleElement().satisfies(action -> {
            assertThat(action.getActionType()).isEqualTo("REALLOCATION");
            assertThat(action.getSourceWarehouseName()).isEqualTo("성남센터");
            assertThat(action.getTargetSalesPointName()).isEqualTo("그리팅몰");
        });
    }

    @Test
    void readsInventoryPerformanceAndAtMostNinetyDaysOfSales() {
        List<StrategyExecutionInventoryVO> inventory = mapper.selectInventoryResults(101L);
        List<StrategyExecutionDailySalesVO> sales = mapper.selectDailySales(101L);
        StrategyExecutionPerformanceVO performance = mapper.selectPerformance(1001L);

        assertThat(inventory).singleElement().satisfies(row -> {
            assertThat(row.getBeforeQuantity()).isEqualByComparingTo("100");
            assertThat(row.getCurrentQuantity()).isEqualByComparingTo("80");
        });
        assertThat(sales).extracting(StrategyExecutionDailySalesVO::getSalesDate)
                .containsExactly(java.time.LocalDate.of(2026, 5, 2), java.time.LocalDate.of(2026, 7, 29));
        assertThat(performance.getActualSalesQuantity()).isEqualByComparingTo("12");
        assertThat(performance.getActualRemainingQuantity()).isEqualByComparingTo("88");
    }

    @Test
    void returnsNullForUnknownFinalSelection() {
        assertThat(mapper.selectFinalStrategyExecution(999L)).isNull();
    }
}
