package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.feature.statistics.mapper.InventoryStatisticsAggregationMapper;
import com.stockit.backend.feature.statistics.mapper.StatisticsSnapshotMapper;
import com.stockit.backend.feature.statistics.vo.InventoryStatisticsAggregateVO;
import com.stockit.backend.feature.statistics.vo.InventoryStatisticsDailySalesVO;

@ExtendWith(MockitoExtension.class)
class InventoryStatisticsDemoBackfillServiceImplTest {

    @Mock
    private InventoryStatisticsAggregationMapper aggregationMapper;
    @Mock
    private StatisticsSnapshotMapper snapshotMapper;

    private InventoryStatisticsDemoBackfillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryStatisticsDemoBackfillServiceImpl(
                aggregationMapper,
                snapshotMapper,
                new InventoryStatisticsDemoTrendSimulator(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void createsEachScopeOncePerDateAndSkipsAnExistingDemoDay() {
        LocalDate from = LocalDate.of(2026, 8, 21);
        LocalDate to = LocalDate.of(2026, 8, 23);
        when(aggregationMapper.selectScopeAggregates(to)).thenReturn(List.of(aggregate()));
        when(aggregationMapper.selectNationalDailySales(from, to)).thenReturn(List.of(
                sales(from, "100"), sales(from.plusDays(1), "120"), sales(to, "90")
        ));
        long existingSyncJobId = InventoryStatisticsDemoBackfillServiceImpl
                .demoSyncJobId(from.plusDays(1));
        when(snapshotMapper.selectSnapshotIdsBySyncJobId(anyLong())).thenAnswer(invocation ->
                invocation.getArgument(0, Long.class) == existingSyncJobId
                        ? List.of(99L)
                        : List.of()
        );
        when(snapshotMapper.selectNextSnapshotId()).thenReturn(1L, 2L);

        var response = service.backfill(from, to);

        assertThat(response.requestedDateCount()).isEqualTo(3);
        assertThat(response.createdDateCount()).isEqualTo(2);
        assertThat(response.skippedDateCount()).isEqualTo(1);
        assertThat(response.createdSnapshotCount()).isEqualTo(2);
        verify(snapshotMapper, times(2)).insertSnapshot(
                anyLong(), anyLong(), any(LocalDate.class),
                any(InventoryStatisticsAggregateVO.class), anyInt(), any(String.class)
        );
    }

    @Test
    void rejectsMoreThanOneYear() {
        assertThatThrownBy(() -> service.backfill(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2)
        )).isInstanceOf(AppException.class);
    }

    private static InventoryStatisticsAggregateVO aggregate() {
        InventoryStatisticsAggregateVO value = new InventoryStatisticsAggregateVO();
        value.setScopeType("NATIONAL");
        value.setScopeCode("ALL");
        value.setScopeName("전국");
        value.setTotalSkuCount(100);
        value.setTotalStockQty(new BigDecimal("10000"));
        value.setAvailableStockQty(new BigDecimal("9000"));
        value.setCriticalSkuCount(10);
        value.setWarningSkuCount(20);
        value.setNormalSkuCount(40);
        value.setGoodSkuCount(25);
        value.setUnassessedDistributionSkuCount(5);
        value.setCriticalStockQty(new BigDecimal("1500"));
        value.setWarningStockQty(new BigDecimal("2500"));
        value.setNormalStockQty(new BigDecimal("3500"));
        value.setGoodStockQty(new BigDecimal("2000"));
        value.setUnassessedDistributionStockQty(new BigDecimal("500"));
        value.setShortageSkuCount(8);
        value.setExpectedDisposalQty30d(new BigDecimal("300"));
        value.setTotalInventoryCostAmount(new BigDecimal("10000000"));
        value.setCriticalInventoryCostAmount(new BigDecimal("1500000"));
        value.setExpectedDisposalLossAmount30d(new BigDecimal("300000"));
        value.setMissingCostStockQty(BigDecimal.ZERO);
        value.setUnassessedStockQty(new BigDecimal("500"));
        value.setMissingForecastStockQty(new BigDecimal("100"));
        return value;
    }

    private static InventoryStatisticsDailySalesVO sales(LocalDate date, String quantity) {
        InventoryStatisticsDailySalesVO value = new InventoryStatisticsDailySalesVO();
        value.setSalesDate(date);
        value.setSalesQty(new BigDecimal(quantity));
        return value;
    }
}
