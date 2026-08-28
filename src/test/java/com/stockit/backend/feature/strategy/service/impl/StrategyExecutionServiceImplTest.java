package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionResponse;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionPageResponse;
import com.stockit.backend.feature.strategy.mapper.StrategyExecutionMapper;
import com.stockit.backend.feature.strategy.service.StrategyExecutionService;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionActionVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionBaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionDailySalesVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionInventoryVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionPerformanceVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionQuery;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionSummaryVO;

@ExtendWith(MockitoExtension.class)
class StrategyExecutionServiceImplTest {

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 7, 29);

    @Mock
    private StrategyExecutionMapper mapper;

    private StrategyExecutionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-28T15:30:00Z"),
                ZoneId.of("UTC")
        );
        service = new StrategyExecutionServiceImpl(mapper, clock);
    }

    @Test
    void loadsListBasesAndAllActionsWithoutNPlusOne() {
        StrategyExecutionQuery query = new StrategyExecutionQuery(0, 2, null, null, null, "DESC");
        StrategyExecutionBaseVO first = base(101L, 1001L);
        StrategyExecutionBaseVO second = base(102L, 1002L);
        when(mapper.selectFinalStrategyExecutionSummary(query)).thenReturn(summary(2, 1, 1, 3));
        when(mapper.selectFinalStrategyExecutions(query)).thenReturn(List.of(first, second));
        when(mapper.selectSupportedActions(List.of(1001L, 1002L)))
                .thenReturn(List.of(action(11L, 1001L), action(12L, 1001L), action(21L, 1002L)));

        StrategyExecutionPageResponse result = service.findAll(query);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).actions()).hasSize(2);
        assertThat(result.content().get(1).actions()).hasSize(1);
        assertThat(result.content().get(0).inventoryResults()).isEmpty();
        assertThat(result.content().get(0).inventoryTransfers()).isEmpty();
        assertThat(result.content().get(0).salesDaily()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.summary().executionStrategyCount()).isEqualTo(2);
        assertThat(result.summary().inProgressStrategyCount()).isEqualTo(1);
        assertThat(result.summary().attentionStrategyCount()).isEqualTo(1);
        assertThat(result.summary().totalStrategyCount()).isEqualTo(result.totalElements());
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isFalse();
        verify(mapper).selectFinalStrategyExecutionSummary(query);
        verify(mapper).selectFinalStrategyExecutions(query);
        verify(mapper).selectSupportedActions(List.of(1001L, 1002L));
        verify(mapper, never()).selectInventoryResults(anyLong());
        verify(mapper, never()).selectDailySales(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).selectPerformance(anyLong());
    }

    @Test
    void returnsEmptyPageMetadataWithoutRunningListOrActionQueries() {
        StrategyExecutionQuery query = new StrategyExecutionQuery(0, 10, "없음", null, null, "DESC");
        when(mapper.selectFinalStrategyExecutionSummary(query)).thenReturn(summary(0, 0, 0, 0));

        StrategyExecutionPageResponse result = service.findAll(query);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
        verify(mapper, never()).selectFinalStrategyExecutions(query);
        verify(mapper, never()).selectSupportedActions(org.mockito.ArgumentMatchers.anyList());
    }

    private static StrategyExecutionSummaryVO summary(
            long executionCount,
            long inProgressCount,
            long attentionCount,
            long totalCount
    ) {
        StrategyExecutionSummaryVO summary = new StrategyExecutionSummaryVO();
        summary.setExecutionStrategyCount(executionCount);
        summary.setInProgressStrategyCount(inProgressCount);
        summary.setAttentionStrategyCount(attentionCount);
        summary.setTotalStrategyCount(totalCount);
        return summary;
    }

    @Test
    void returnsDetailWithCalculatedProgressStatusAndActualCollectedValues() {
        StrategyExecutionBaseVO base = base(101L, 1001L);
        StrategyExecutionActionVO firstAction = action(11L, 1001L);
        StrategyExecutionActionVO sameRouteAction = action(12L, 1001L);
        sameRouteAction.setActionQuantity(new BigDecimal("-5"));
        StrategyExecutionActionVO anotherRouteAction = action(13L, 1001L);
        anotherRouteAction.setActionQuantity(new BigDecimal("7"));
        anotherRouteAction.setSourceWarehouseId(null);
        anotherRouteAction.setSourceWarehouseName(null);
        anotherRouteAction.setSourceSalesPointId(11L);
        anotherRouteAction.setSourceSalesPointName("부산 매장");
        anotherRouteAction.setTargetSalesPointId(null);
        anotherRouteAction.setTargetSalesPointName(null);
        anotherRouteAction.setDestinationWarehouseId(502L);
        anotherRouteAction.setDestinationWarehouseName("부산센터");
        StrategyExecutionInventoryVO sourceInventory = inventory(
                "WAREHOUSE", 501L, "성남센터", "100", "75", "30"
        );
        StrategyExecutionInventoryVO targetInventory = inventory(
                "SALES_POINT", 10L, "그리팅몰", "100", "125", "20"
        );
        StrategyExecutionDailySalesVO sales = new StrategyExecutionDailySalesVO();
        sales.setSalesDate(LocalDate.of(2026, 5, 2));
        sales.setSalesPointId(10L);
        sales.setSalesPointCode("GREETING");
        sales.setSalesPointName("그리팅몰");
        sales.setQuantity(new BigDecimal("7"));
        sales.setRevenue(new BigDecimal("70000"));
        StrategyExecutionPerformanceVO performance = new StrategyExecutionPerformanceVO();
        performance.setPerformanceCount(1L);
        performance.setActualSalesQuantity(new BigDecimal("7"));
        performance.setActualRemainingQuantity(new BigDecimal("80"));

        when(mapper.selectFinalStrategyExecution(101L)).thenReturn(base);
        when(mapper.selectSupportedActions(List.of(1001L)))
                .thenReturn(List.of(firstAction, sameRouteAction, anotherRouteAction));
        when(mapper.selectInventoryResults(101L)).thenReturn(List.of(sourceInventory, targetInventory));
        when(mapper.selectDailySales(101L, AS_OF_DATE)).thenReturn(List.of(sales));
        when(mapper.selectPerformance(1001L)).thenReturn(performance);

        StrategyExecutionResponse result = service.findByStrategyCaseId(101L);

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.progress()).isEqualTo(100);
        assertThat(result.resultSummary()).isEqualTo("실제 판매 7 / 목표 10 (달성률 70%)");
        assertThat(result.actions().get(0).status()).isEqualTo("COMPLETED");
        assertThat(result.actions().get(0).progress()).isEqualTo(100);
        assertThat(result.actions().get(0).relationship()).isNull();
        assertThat(result.inventoryResults().get(0).moved()).isEqualByComparingTo("-25");
        assertThat(result.inventoryResults().get(0).guardrail())
                .isEqualTo("안전재고 기준 30");
        assertThat(result.inventoryResults().get(1).moved()).isEqualByComparingTo("25");
        assertThat(result.inventoryTransfers()).hasSize(2);
        assertThat(result.inventoryTransfers().get(0)).satisfies(transfer -> {
            assertThat(transfer.actionType()).isEqualTo("RT_TRANSFER");
            assertThat(transfer.fromLocationId()).isEqualTo(501L);
            assertThat(transfer.fromLocationName()).isEqualTo("성남센터");
            assertThat(transfer.toLocationId()).isEqualTo(502L);
            assertThat(transfer.toLocationName()).isEqualTo("경인1센터");
            assertThat(transfer.sourceWarehouseId()).isEqualTo(501L);
            assertThat(transfer.sourceWarehouseName()).isEqualTo("성남센터");
            assertThat(transfer.destinationWarehouseId()).isEqualTo(502L);
            assertThat(transfer.destinationWarehouseName()).isEqualTo("경인1센터");
            assertThat(transfer.targetSalesPointId()).isEqualTo(10L);
            assertThat(transfer.targetSalesPointName()).isEqualTo("그리팅몰");
            assertThat(transfer.quantity()).isEqualByComparingTo("25");
        });
        assertThat(result.inventoryTransfers().get(1)).satisfies(transfer -> {
            assertThat(transfer.fromLocationId()).isEqualTo(11L);
            assertThat(transfer.toLocationId()).isEqualTo(502L);
            assertThat(transfer.destinationWarehouseId()).isEqualTo(502L);
            assertThat(transfer.targetSalesPointId()).isNull();
            assertThat(transfer.quantity()).isEqualByComparingTo("7");
        });
        assertThat(result.inventoryTransfers())
                .allSatisfy(transfer -> assertThat(transfer.quantity()).isPositive());
        assertThat(result.salesDaily()).hasSize(1);
        assertThat(result.channelResults().get(0).sales()).isEqualByComparingTo("7");
        assertThat(result.performance().actualRemainingQuantity()).isEqualByComparingTo("80");
        assertThat(result.lastSyncedAt()).isNotNull();
        verify(mapper).selectDailySales(101L, AS_OF_DATE);
        verify(mapper).selectSupportedActions(List.of(1001L));
    }

    @Test
    void returnsEmptyTransfersWhenStrategyHasNoInventoryMovementAction() {
        StrategyExecutionBaseVO base = base(101L, 1001L);
        StrategyExecutionActionVO discount = action(11L, 1001L);
        discount.setActionType("PRICE_DISCOUNT");
        when(mapper.selectFinalStrategyExecution(101L)).thenReturn(base);
        when(mapper.selectSupportedActions(List.of(1001L))).thenReturn(List.of(discount));

        StrategyExecutionResponse result = service.findByStrategyCaseId(101L);

        assertThat(result.inventoryTransfers()).isEmpty();
    }

    @Test
    void rejectsCaseWithoutFinalSelectionAsStandardNotFound() {
        when(mapper.selectFinalStrategyExecution(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.findByStrategyCaseId(999L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_EXECUTION_NOT_FOUND));
    }

    @Test
    void rejectsFinalSelectionWithoutStrategyOptionAsStandardNotFound() {
        when(mapper.selectFinalStrategyExecution(101L)).thenReturn(base(101L, null));

        assertThatThrownBy(() -> service.findByStrategyCaseId(101L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_EXECUTION_NOT_FOUND));
    }

    private static StrategyExecutionBaseVO base(Long caseId, Long optionId) {
        StrategyExecutionBaseVO base = new StrategyExecutionBaseVO();
        base.setStrategyCaseId(caseId);
        base.setStrategyOptionId(optionId);
        base.setCaseCode("SC-" + caseId);
        base.setCaseStatus("EXECUTING");
        base.setEstablishedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        base.setLastSyncedAt(LocalDateTime.of(2026, 5, 3, 10, 0));
        base.setSkuId(1L);
        base.setSkuCode("SKU-1");
        base.setSkuName("테스트 SKU");
        base.setUnitCode("개");
        base.setProductName("테스트 상품");
        base.setRecommendationReason("재고 편중 완화");
        base.setPlannedStartDate(LocalDate.of(2026, 5, 1));
        base.setPlannedEndDate(LocalDate.of(2026, 5, 10));
        base.setGoalTargetValue(new BigDecimal("10"));
        base.setGoalActualValue(new BigDecimal("7"));
        base.setAchievementRate(new BigDecimal("70"));
        return base;
    }

    private static StrategyExecutionActionVO action(Long actionId, Long optionId) {
        StrategyExecutionActionVO action = new StrategyExecutionActionVO();
        action.setStrategyActionId(actionId);
        action.setStrategyOptionId(optionId);
        action.setActionType("RT_TRANSFER");
        action.setActionQuantity(new BigDecimal("20"));
        action.setSourceWarehouseId(501L);
        action.setSourceWarehouseName("성남센터");
        action.setDestinationWarehouseId(502L);
        action.setDestinationWarehouseName("경인1센터");
        action.setTargetSalesPointId(10L);
        action.setTargetSalesPointCode("GREETING");
        action.setTargetSalesPointName("그리팅몰");
        action.setStartDate(LocalDate.of(2026, 5, 1));
        action.setEndDate(LocalDate.of(2026, 5, 10));
        return action;
    }

    private static StrategyExecutionInventoryVO inventory(
            String type,
            Long id,
            String name,
            String before,
            String current,
            String safetyStock
    ) {
        StrategyExecutionInventoryVO inventory = new StrategyExecutionInventoryVO();
        inventory.setLocationType(type);
        inventory.setLocationId(id);
        inventory.setLocationName(name);
        inventory.setBeforeQuantity(new BigDecimal(before));
        inventory.setCurrentQuantity(new BigDecimal(current));
        inventory.setSafetyStockQuantity(new BigDecimal(safetyStock));
        return inventory;
    }
}
