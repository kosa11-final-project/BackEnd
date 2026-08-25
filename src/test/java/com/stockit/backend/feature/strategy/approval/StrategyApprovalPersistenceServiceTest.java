package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.CaseRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ExistingSelectionRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.SimulationWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ExecutionResultWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.OptionWrite;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.FinalSelectionWrite;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

@ExtendWith(MockitoExtension.class)
class StrategyApprovalPersistenceServiceTest {

    @Mock private StrategyApprovalMapper approvalMapper;
    @Mock private StrategyGenerationResult.Option option;
    @Mock private StrategyGenerationResult.Candidate candidate;
    @Mock private StrategyCalculationContext context;
    @Mock private ResolvedStrategySelection resolved;
    @Mock private StrategySelectionExecutabilityValidator executabilityValidator;
    @Mock private StrategyDateTimeProvider dateTimeProvider;

    private StrategyApprovalPersistenceService service;
    private AiStrategyReviewerVO reviewer;

    @BeforeEach
    void setUp() {
        service = new StrategyApprovalPersistenceService(
                approvalMapper,
                new ObjectMapper(),
                executabilityValidator,
                dateTimeProvider
        );
        reviewer = new AiStrategyReviewerVO();
        reviewer.setReviewerId(7L);
        reviewer.setReviewerName("검토자");
        reviewer.setEmail("reviewer@stockit.test");
        reviewer.setOrganizationId(1L);
    }

    @Test
    void rejectsChangingOptionAfterFinalSelectionWasPersisted() {
        stubValidCase();
        when(option.rank()).thenReturn(2);
        when(approvalMapper.selectExistingSelection(123L))
                .thenReturn(existingSelection(1, "CAND-1"));

        assertThatThrownBy(() -> service.prepare(
                123L, 3L, 1L, resolved, List.of(reviewer)
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);

        verify(approvalMapper, never()).insertReviewRequest(any());
    }

    @Test
    void reusesSameSelectionAndCreatesOnlyMissingReviewerRequest() {
        stubValidCase();
        when(option.rank()).thenReturn(1);
        when(option.candidate()).thenReturn(candidate);
        when(candidate.candidateId()).thenReturn("CAND-1");
        when(approvalMapper.selectExistingSelection(123L))
                .thenReturn(existingSelection(1, "CAND-1"));
        ReviewRequestRecord request = reviewRequest();
        when(approvalMapper.selectAllReviewRequests(55L))
                .thenReturn(List.of(request));
        when(approvalMapper.selectReviewRequests(55L, List.of(7L)))
                .thenReturn(List.of(request));

        PreparedStrategyApproval prepared = service.prepare(
                123L, 3L, 1L, resolved, List.of(reviewer)
        );

        assertThat(prepared.finalSelectionId()).isEqualTo(44L);
        assertThat(prepared.strategyOptionId()).isEqualTo(55L);
        assertThat(prepared.reviewRequests()).containsExactly(request);
        verify(approvalMapper, never())
                .insertReviewRequest(any(ReviewRequestWrite.class));
    }

    @Test
    void rejectsDifferentCandidateEvenWhenOptionRankMatches() {
        stubValidCase();
        when(option.rank()).thenReturn(1);
        when(option.candidate()).thenReturn(candidate);
        when(candidate.candidateId()).thenReturn("CAND-2");
        when(approvalMapper.selectExistingSelection(123L))
                .thenReturn(existingSelection(1, "CAND-1"));

        assertThatThrownBy(() -> service.prepare(
                123L, 3L, 1L, resolved, List.of(reviewer)
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);

        verify(approvalMapper, never()).insertReviewRequest(any());
    }

    @Test
    void persistsResolvedUserSelectionAndInitialExecutionResult() {
        stubValidCase();
        StrategyCandidateSimulation simulation = org.mockito.Mockito.mock(
                StrategyCandidateSimulation.class
        );
        StrategyCandidateSimulation.Summary summary = org.mockito.Mockito.mock(
                StrategyCandidateSimulation.Summary.class
        );
        BaselineSimulation baseline = org.mockito.Mockito.mock(
                BaselineSimulation.class
        );
        BaselineSimulation.Summary baselineSummary = org.mockito.Mockito.mock(
                BaselineSimulation.Summary.class
        );
        when(option.rank()).thenReturn(1);
        when(option.optionName()).thenReturn("사용자 조정 전략");
        when(option.candidate()).thenReturn(candidate);
        when(option.simulation()).thenReturn(simulation);
        when(candidate.candidateId()).thenReturn("CAND-1");
        when(candidate.startDate()).thenReturn(LocalDate.of(2026, 8, 25));
        when(candidate.actions()).thenReturn(List.of());
        when(candidate.assumptions()).thenReturn(List.of());
        when(simulation.summary()).thenReturn(summary);
        when(summary.expectedSalesQty()).thenReturn(new BigDecimal("29"));
        when(summary.expectedRevenue()).thenReturn(new BigDecimal("246500"));
        when(summary.totalContributionMargin()).thenReturn(new BigDecimal("58000"));
        when(summary.contributionMarginRate()).thenReturn(new BigDecimal("0.235294"));
        when(summary.expectedRemainingQty()).thenReturn(new BigDecimal("1"));
        when(resolved.inputSource()).thenReturn(
                StrategySelectionInputSource.USER_SELECT
        );
        when(resolved.targetQuantity()).thenReturn(new BigDecimal("29"));
        when(resolved.evaluationEndDate()).thenReturn(
                LocalDate.of(2026, 8, 31)
        );
        when(resolved.recommendationSource()).thenReturn(
                StrategyRecommendationSource.LLM
        );
        when(resolved.baselineSimulation()).thenReturn(baseline);
        when(baseline.summary()).thenReturn(baselineSummary);
        when(baselineSummary.expectedDisposalQty()).thenReturn(BigDecimal.ZERO);
        when(context.unitCost()).thenReturn(new BigDecimal("5000"));
        when(context.evaluationInventory()).thenReturn(List.of());
        when(context.salesPoints()).thenReturn(java.util.Map.of());
        when(approvalMapper.selectExistingSelection(123L)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<OptionWrite>getArgument(0).setStrategyOptionId(55L);
            return null;
        }).when(approvalMapper).insertOption(any(OptionWrite.class));
        doAnswer(invocation -> {
            invocation.<FinalSelectionWrite>getArgument(0).setFinalSelectionId(44L);
            return null;
        }).when(approvalMapper).insertFinalSelection(any(FinalSelectionWrite.class));
        ReviewRequestRecord request = reviewRequest();
        when(approvalMapper.selectReviewRequests(55L, List.of(7L)))
                .thenReturn(List.of(), List.of(request));

        PreparedStrategyApproval prepared = service.prepare(
                123L, 3L, 1L, resolved, List.of(reviewer)
        );

        ArgumentCaptor<SimulationWrite> simulationCaptor =
                ArgumentCaptor.forClass(SimulationWrite.class);
        verify(approvalMapper).insertSimulation(simulationCaptor.capture());
        assertThat(simulationCaptor.getValue().getInputSourceType())
                .isEqualTo("USER_SELECT");
        assertThat(simulationCaptor.getValue().getTargetQuantity())
                .isEqualByComparingTo("29");

        ArgumentCaptor<ExecutionResultWrite> executionCaptor =
                ArgumentCaptor.forClass(ExecutionResultWrite.class);
        verify(approvalMapper).insertExecutionResult(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getPlannedStartDate())
                .isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(executionCaptor.getValue().getPlannedEndDate())
                .isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(executionCaptor.getValue().getGoalTargetValue())
                .isEqualByComparingTo("29");
        assertThat(prepared.finalSelectionId()).isEqualTo(44L);
    }

    @Test
    void truncatesTextByUtf8BytesWithoutSplittingCharacters() {
        String korean = "한".repeat(667);
        String emoji = "😀".repeat(501);

        String truncatedKorean = StrategyApprovalPersistenceService.truncateUtf8(
                korean, 2_000
        );
        String truncatedEmoji = StrategyApprovalPersistenceService.truncateUtf8(
                emoji, 2_000
        );

        assertThat(truncatedKorean).hasSize(666);
        assertThat(truncatedKorean.getBytes(StandardCharsets.UTF_8)).hasSize(1_998);
        assertThat(truncatedEmoji.codePointCount(0, truncatedEmoji.length()))
                .isEqualTo(500);
        assertThat(truncatedEmoji.getBytes(StandardCharsets.UTF_8)).hasSize(2_000);
    }

    private void stubValidCase() {
        when(approvalMapper.selectCaseForUpdate(123L)).thenReturn(strategyCase());
        when(resolved.option()).thenReturn(option);
        when(resolved.calculationContext()).thenReturn(context);
        lenient().when(resolved.selectionFingerprint())
                .thenReturn("fingerprint-1");
        lenient().when(resolved.businessDate())
                .thenReturn(LocalDate.of(2026, 8, 25));
        when(context.strategyCaseId()).thenReturn(123L);
        when(dateTimeProvider.now()).thenReturn(LocalDateTime.of(
                2026, 8, 25, 12, 0
        ));
    }

    private static CaseRecord strategyCase() {
        CaseRecord strategyCase = new CaseRecord();
        strategyCase.setStrategyCaseId(123L);
        strategyCase.setSkuId(1001L);
        strategyCase.setCaseName("테스트 Case");
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATED);
        strategyCase.setGenerationStage(StrategyGenerationStage.COMPARISON_READY);
        strategyCase.setResultExpiresAt(LocalDateTime.of(2026, 8, 28, 12, 0));
        strategyCase.setResultCacheKey("ai-strategy:case:123:result:v1");
        strategyCase.setRequesterOrganizationId(1L);
        return strategyCase;
    }

    private static ExistingSelectionRecord existingSelection(
            int optionRank,
            String candidateId
    ) {
        ExistingSelectionRecord selection = new ExistingSelectionRecord();
        selection.setFinalSelectionId(44L);
        selection.setStrategyOptionId(55L);
        selection.setOptionRank(optionRank);
        selection.setConstraintText(
                "candidateId=" + candidateId + "\nassumptions="
        );
        return selection;
    }

    private static ReviewRequestRecord reviewRequest() {
        ReviewRequestRecord request = new ReviewRequestRecord();
        request.setReviewRequestId(701L);
        request.setReviewerId(7L);
        request.setReviewStatus(StrategyReviewStatus.PENDING);
        return request;
    }
}
