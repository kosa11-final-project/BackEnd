package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.statistics.service.StrategyExecutionResultService;
import com.stockit.backend.feature.strategy.mapper.StrategyPerformanceSyncMapper;
import com.stockit.backend.feature.strategy.vo.StrategyPerformanceSyncRowVO;

@ExtendWith(MockitoExtension.class)
class StrategyPerformanceSyncServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-26T06:30:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 26);

    @Mock
    private StrategyPerformanceSyncMapper mapper;
    @Mock
    private StrategyExecutionResultService resultService;

    private StrategyPerformanceSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyPerformanceSyncServiceImpl(
                mapper,
                resultService,
                Clock.fixed(NOW, ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void updatesThenInsertsDailyRowsAndMarksOnlySuccessfulSelectionsSynced() {
        StrategyPerformanceSyncRowVO existing = row(10L, 100L, LocalDate.of(2026, 8, 25), "1000");
        StrategyPerformanceSyncRowVO newRow = row(10L, 100L, BUSINESS_DATE, "1200");
        when(mapper.lockSyncMutex()).thenReturn(1);
        when(mapper.countEligibleSelections(BUSINESS_DATE)).thenReturn(1);
        when(mapper.selectPerformanceRows(BUSINESS_DATE)).thenReturn(List.of(existing, newRow));
        when(mapper.updatePerformance(existing, 7L)).thenReturn(1);
        when(mapper.updatePerformance(newRow, 7L)).thenReturn(0);
        when(mapper.insertPerformanceIfAbsent(newRow, 7L)).thenReturn(1);

        var response = service.synchronize(7L);

        verify(mapper).lockFinalSelections(List.of(10L));
        verify(mapper).updateLastSyncedAt(List.of(10L), 7L, NOW);
        verify(resultService).process(BUSINESS_DATE);
        assertThat(response.processedStrategyCount()).isEqualTo(1);
        assertThat(response.updatedPerformanceCount()).isEqualTo(2);
        assertThat(response.skippedStrategyCount()).isZero();
        assertThat(response.warnings()).isEmpty();
    }

    @Test
    void skipsASelectionWhenContributionMarginCannotBeCalculated() {
        StrategyPerformanceSyncRowVO missingPrice = row(20L, 200L, BUSINESS_DATE, null);
        when(mapper.lockSyncMutex()).thenReturn(1);
        when(mapper.countEligibleSelections(BUSINESS_DATE)).thenReturn(1);
        when(mapper.selectPerformanceRows(BUSINESS_DATE)).thenReturn(List.of(missingPrice));

        var response = service.synchronize(7L);

        verify(mapper, never()).lockFinalSelections(List.of(20L));
        verify(mapper, never()).updatePerformance(missingPrice, 7L);
        verify(resultService).process(BUSINESS_DATE);
        assertThat(response.processedStrategyCount()).isZero();
        assertThat(response.skippedStrategyCount()).isEqualTo(1);
        assertThat(response.warnings()).singleElement().asString().contains("가격·원가");
    }

    @Test
    void rejectsAnUnauthenticatedRequest() {
        assertThatThrownBy(() -> service.synchronize(null))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).getErrorCode())
                        .isEqualTo(ErrorCode.AUTHENTICATION_FAILED));
    }

    @Test
    void reportsAConflictWhenAnotherSynchronizationOwnsTheMutex() {
        when(mapper.lockSyncMutex()).thenThrow(new PessimisticLockingFailureException("ORA-00054"));

        assertThatThrownBy(() -> service.synchronize(7L))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).getErrorCode())
                        .isEqualTo(ErrorCode.AI_STRATEGY_PERFORMANCE_SYNC_CONFLICT));
    }

    private static StrategyPerformanceSyncRowVO row(
            Long finalSelectionId,
            Long strategyOptionId,
            LocalDate date,
            String margin
    ) {
        StrategyPerformanceSyncRowVO row = new StrategyPerformanceSyncRowVO();
        row.setFinalSelectionId(finalSelectionId);
        row.setStrategyOptionId(strategyOptionId);
        row.setPerformanceDate(date);
        row.setActualSalesQuantity(BigDecimal.TEN);
        row.setActualRevenue(new BigDecimal("10000"));
        row.setActualContributionMargin(margin == null ? null : new BigDecimal(margin));
        row.setActualRemainingQuantity(new BigDecimal("20"));
        return row;
    }
}
