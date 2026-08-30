package com.stockit.backend.feature.strategy.mapper;

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

import com.stockit.backend.feature.strategy.vo.StrategyExecutionActionVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionBaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionDailySalesVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionInventoryVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionPerformanceVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionQuery;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionSummaryVO;
import com.stockit.backend.feature.strategy.vo.StrategyPerformanceSyncRowVO;

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

    @Autowired
    private StrategyPerformanceSyncMapper performanceSyncMapper;

    @Test
    void readsOnlyFinalSelectionsAndSupportedActionsInBulk() {
        StrategyExecutionQuery query = query(0, 10, null, null, null, "DESC");
        List<StrategyExecutionBaseVO> bases = mapper.selectFinalStrategyExecutions(query);
        List<StrategyExecutionActionVO> actions = mapper.selectSupportedActions(List.of(1001L));

        assertThat(mapper.countFinalStrategyExecutions(query)).isEqualTo(3);
        assertThat(bases).extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(102L, 101L, 103L);
        assertThat(bases.get(1)).satisfies(base -> {
            assertThat(base.getStrategyCaseId()).isEqualTo(101L);
            assertThat(base.getImageUrl()).isEqualTo("https://example.com/image.jpg");
            assertThat(base.getLastSyncedAt()).isNotNull();
        });
        assertThat(actions).extracting(StrategyExecutionActionVO::getActionType)
                .containsExactly("REALLOCATION", "PRICE_DISCOUNT");
        assertThat(actions.get(0).getSourceWarehouseName()).isEqualTo("성남센터");
        assertThat(actions.get(0).getTargetSalesPointName()).isEqualTo("그리팅몰");
    }

    @Test
    void aggregatesDistinctStrategiesUsingOnlyLatestExecutionResult() {
        StrategyExecutionQuery query = query(0, 10, null, null, null, "DESC");

        StrategyExecutionSummaryVO summary = mapper.selectFinalStrategyExecutionSummary(query);

        assertThat(summary.getExecutionStrategyCount()).isEqualTo(2);
        assertThat(summary.getInProgressStrategyCount()).isEqualTo(1);
        assertThat(summary.getAttentionStrategyCount()).isEqualTo(1);
        assertThat(summary.getTotalStrategyCount()).isEqualTo(3);
    }

    @Test
    void appliesTheSameFiltersToListCountAndSummary() {
        StrategyExecutionQuery query = query(0, 10, null, null, "PRICE_DISCOUNT", "DESC");

        StrategyExecutionSummaryVO summary = mapper.selectFinalStrategyExecutionSummary(query);

        assertThat(summary.getExecutionStrategyCount()).isEqualTo(1);
        assertThat(summary.getInProgressStrategyCount()).isEqualTo(1);
        assertThat(summary.getAttentionStrategyCount()).isEqualTo(1);
        assertThat(summary.getTotalStrategyCount()).isEqualTo(mapper.countFinalStrategyExecutions(query));
    }

    @Test
    void readsMovementEndpointsAndQuantityWithoutAdditionalLocationQueries() {
        List<StrategyExecutionActionVO> actions = mapper.selectSupportedActions(List.of(1001L));

        assertThat(actions.get(0)).satisfies(action -> {
            assertThat(action.getActionType()).isEqualTo("REALLOCATION");
            assertThat(action.getActionQuantity()).isEqualByComparingTo("20");
            assertThat(action.getSourceWarehouseId()).isEqualTo(501L);
            assertThat(action.getSourceWarehouseName()).isEqualTo("성남센터");
            assertThat(action.getDestinationWarehouseId()).isEqualTo(502L);
            assertThat(action.getDestinationWarehouseName()).isEqualTo("경인1센터");
            assertThat(action.getTargetSalesPointId()).isEqualTo(10L);
            assertThat(action.getTargetSalesPointName()).isEqualTo("그리팅몰");
        });
    }

    @Test
    void appliesPaginationSearchAndFiltersWithAndSemantics() {
        StrategyExecutionQuery firstPage = query(0, 1, null, null, "PRICE_DISCOUNT", "DESC");
        StrategyExecutionQuery secondPage = query(1, 1, null, null, "PRICE_DISCOUNT", "DESC");

        assertThat(mapper.countFinalStrategyExecutions(firstPage)).isEqualTo(2);
        assertThat(mapper.selectFinalStrategyExecutions(firstPage))
                .extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(102L);
        assertThat(mapper.selectFinalStrategyExecutions(secondPage))
                .extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(101L);

        StrategyExecutionQuery combined = query(
                0, 10, "  두부  ", "READY_TO_EXECUTE", "PRICE_DISCOUNT", "DESC"
        );
        assertThat(mapper.countFinalStrategyExecutions(combined)).isEqualTo(1);
        assertThat(mapper.selectFinalStrategyExecutions(combined))
                .extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(102L);
    }

    @Test
    void searchesByStrategyCodeSkuCodeAndProductName() {
        StrategyExecutionQuery bySku = query(0, 10, "SKU-1", null, null, "DESC");
        assertThat(mapper.selectFinalStrategyExecutions(bySku))
                .extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(101L);

        StrategyExecutionQuery byStrategyCode = query(0, 10, "SC-101", null, null, "DESC");
        assertThat(mapper.selectFinalStrategyExecutions(byStrategyCode))
                .extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(101L);

        StrategyExecutionQuery byProductName = query(0, 10, "두부", null, null, "DESC");
        assertThat(mapper.selectFinalStrategyExecutions(byProductName))
                .extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(102L);

        StrategyExecutionQuery ascending = query(0, 10, null, null, null, "ASC");
        assertThat(mapper.selectFinalStrategyExecutions(ascending))
                .extracting(StrategyExecutionBaseVO::getStrategyCaseId)
                .containsExactly(103L, 101L, 102L);
    }

    @Test
    void readsInventoryPerformanceAndSalesOnlyInsideExecutionPeriod() {
        List<StrategyExecutionInventoryVO> inventory = mapper.selectInventoryResults(101L);
        LocalDate asOfDate = LocalDate.of(2026, 7, 28);
        List<StrategyExecutionDailySalesVO> sales = mapper.selectDailySales(101L, asOfDate);
        StrategyExecutionPerformanceVO performance = mapper.selectPerformance(1001L);

        assertThat(inventory)
                .extracting(StrategyExecutionInventoryVO::getLocationType, StrategyExecutionInventoryVO::getLocationId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SALES_POINT", 10L),
                        org.assertj.core.groups.Tuple.tuple("WAREHOUSE", 502L),
                        org.assertj.core.groups.Tuple.tuple("WAREHOUSE", 501L)
                );
        assertThat(inventory).filteredOn(row -> Long.valueOf(501L).equals(row.getLocationId()))
                .singleElement().satisfies(row -> {
                    assertThat(row.getBeforeQuantity()).isEqualByComparingTo("100");
                    assertThat(row.getCurrentQuantity()).isEqualByComparingTo("80");
                });
        assertThat(sales).extracting(StrategyExecutionDailySalesVO::getSalesDate)
                .containsExactly(LocalDate.of(2026, 5, 2));
        assertThat(performance.getActualSalesQuantity()).isEqualByComparingTo("12");
        assertThat(performance.getActualRemainingQuantity()).isEqualByComparingTo("88");
    }

    @Test
    void buildsDailyPerformanceFromScopedSalesAndCurrentInventory() {
        LocalDate businessDate = LocalDate.of(2026, 8, 26);

        List<StrategyPerformanceSyncRowVO> rows = performanceSyncMapper.selectPerformanceRows(businessDate);

        assertThat(performanceSyncMapper.countEligibleSelections(businessDate)).isEqualTo(3);
        assertThat(rows).filteredOn(row -> row.getFinalSelectionId().equals(5001L))
                .anySatisfy(row -> {
                    assertThat(row.getPerformanceDate()).isEqualTo(LocalDate.of(2026, 5, 2));
                    assertThat(row.getActualSalesQuantity()).isEqualByComparingTo("7");
                    assertThat(row.getActualRevenue()).isEqualByComparingTo("70000");
                    assertThat(row.getActualContributionMargin()).isEqualByComparingTo("28000");
                })
                .anySatisfy(row -> {
                    assertThat(row.getPerformanceDate()).isEqualTo(businessDate);
                    assertThat(row.getActualRemainingQuantity()).isEqualByComparingTo("80");
                });
    }

    @Test
    void returnsNullForUnknownFinalSelection() {
        assertThat(mapper.selectFinalStrategyExecution(999L)).isNull();
    }

    private static StrategyExecutionQuery query(
            int page,
            int size,
            String query,
            String status,
            String actionType,
            String direction
    ) {
        return new StrategyExecutionQuery(page, size, query == null ? null : query.trim(), status, actionType, direction);
    }
}
