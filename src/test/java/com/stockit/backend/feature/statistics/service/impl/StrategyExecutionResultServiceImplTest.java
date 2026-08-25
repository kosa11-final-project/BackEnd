package com.stockit.backend.feature.statistics.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.statistics.mapper.StrategyStatisticsMapper;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionDueResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionStartCandidateVO;

@ExtendWith(MockitoExtension.class)
class StrategyExecutionResultServiceImplTest {

    @Mock
    private StrategyStatisticsMapper mapper;

    private StrategyExecutionResultServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyExecutionResultServiceImpl(mapper);
    }

    @Test
    void capturesNewStartsAndFinalizesSalesOnlyAchievementAndSavings() {
        LocalDate businessDate = LocalDate.of(2026, 8, 23);
        StrategyExecutionStartCandidateVO start = new StrategyExecutionStartCandidateVO();
        start.setFinalSelectionId(10L);

        StrategyExecutionDueResultVO due = new StrategyExecutionDueResultVO();
        due.setFinalSelectionId(20L);
        due.setGoalTargetValue(new BigDecimal("200"));
        due.setGoalActualValue(new BigDecimal("150"));
        due.setStartExpectedDisposalQty(new BigDecimal("30"));
        due.setEndExpectedDisposalQty(new BigDecimal("10"));
        due.setStartUnitCost(new BigDecimal("2500"));

        when(mapper.selectExecutionStartCandidates(businessDate)).thenReturn(List.of(start));
        when(mapper.selectDueExecutionResults(businessDate)).thenReturn(List.of(due));
        when(mapper.completeExecutionResult(eq(due), any(), any())).thenReturn(1);

        service.process(businessDate);

        verify(mapper).insertExecutionStartResult(start);
        ArgumentCaptor<BigDecimal> rate = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> savings = ArgumentCaptor.forClass(BigDecimal.class);
        verify(mapper).completeExecutionResult(eq(due), rate.capture(), savings.capture());
        assertThat(rate.getValue()).isEqualByComparingTo("75.000000");
        assertThat(savings.getValue()).isEqualByComparingTo("50000.00");
    }

    @Test
    void doesNotFinalizeWhenNoPostEndSynchronizationIsReady() {
        LocalDate businessDate = LocalDate.of(2026, 8, 23);
        when(mapper.selectExecutionStartCandidates(businessDate)).thenReturn(List.of());
        when(mapper.selectDueExecutionResults(businessDate)).thenReturn(List.of());

        service.process(businessDate);

        verify(mapper).selectDueExecutionResults(businessDate);
    }
}
