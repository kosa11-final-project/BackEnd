package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.ResolvedStrategySelection;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.CaseRecord;
import com.stockit.backend.feature.strategy.approval.StrategySelectionExecutabilityValidator;
import com.stockit.backend.feature.strategy.approval.StrategySelectionInputSource;
import com.stockit.backend.feature.strategy.approval.StrategySelectionResolver;
import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

@ExtendWith(MockitoExtension.class)
class AiStrategySelectionValidationServiceImplTest {

    @Mock private StrategyApprovalMapper approvalMapper;
    @Mock private StrategySelectionResolver selectionResolver;
    @Mock private StrategySelectionExecutabilityValidator executabilityValidator;
    @Mock private StrategyDateTimeProvider dateTimeProvider;
    @Mock private ResolvedStrategySelection resolved;
    @Mock private StrategyGenerationResult.Option option;
    @Mock private StrategyGenerationResult.Candidate candidate;

    private AiStrategySelectionValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiStrategySelectionValidationServiceImpl(
                approvalMapper,
                selectionResolver,
                executabilityValidator,
                dateTimeProvider
        );
    }

    @Test
    void validatesOriginalSelectionWithoutPersistingIt() {
        LocalDate businessDate = LocalDate.of(2026, 8, 25);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        LocalDateTime validatedAt = LocalDateTime.of(2026, 8, 25, 18, 30);
        when(approvalMapper.selectCase(123L)).thenReturn(caseRecord(1L));
        when(selectionResolver.resolve(123L, "CAND-1", null))
                .thenReturn(resolved);
        when(resolved.businessDate()).thenReturn(businessDate);
        when(resolved.option()).thenReturn(option);
        when(option.candidate()).thenReturn(candidate);
        when(candidate.candidateId()).thenReturn("CAND-1");
        when(candidate.startDate()).thenReturn(businessDate);
        when(resolved.inputSource()).thenReturn(
                StrategySelectionInputSource.AI_RECOMMENDED
        );
        when(resolved.targetQuantity()).thenReturn(new BigDecimal("29"));
        when(resolved.evaluationEndDate()).thenReturn(endDate);
        when(dateTimeProvider.now()).thenReturn(validatedAt);

        var response = service.validate(123L, "CAND-1", null, 1L);

        verify(executabilityValidator).validate(resolved, businessDate);
        assertThat(response.valid()).isTrue();
        assertThat(response.optionId()).isEqualTo("CAND-1");
        assertThat(response.selectionSource())
                .isEqualTo(StrategySelectionInputSource.AI_RECOMMENDED);
        assertThat(response.actionQuantity()).isEqualByComparingTo("29");
        assertThat(response.validatedAt()).isEqualTo(validatedAt);
    }

    @Test
    void rejectsAnotherOrganizationsCaseBeforeLoadingRedisSelection() {
        when(approvalMapper.selectCase(123L)).thenReturn(caseRecord(2L));

        assertThatThrownBy(() -> service.validate(123L, "CAND-1", null, 1L))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(selectionResolver, executabilityValidator);
    }

    private static CaseRecord caseRecord(Long organizationId) {
        CaseRecord strategyCase = new CaseRecord();
        strategyCase.setStrategyCaseId(123L);
        strategyCase.setRequesterOrganizationId(organizationId);
        return strategyCase;
    }
}
