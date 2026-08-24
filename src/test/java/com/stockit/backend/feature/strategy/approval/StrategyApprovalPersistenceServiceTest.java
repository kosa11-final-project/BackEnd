package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.CaseRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ExistingSelectionRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestWrite;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

@ExtendWith(MockitoExtension.class)
class StrategyApprovalPersistenceServiceTest {

    @Mock private StrategyApprovalMapper approvalMapper;
    @Mock private StrategyGenerationResult.Option option;
    @Mock private StrategyGenerationResult.Candidate candidate;
    @Mock private StrategyCalculationContext context;

    private StrategyApprovalPersistenceService service;
    private AiStrategyReviewerVO reviewer;

    @BeforeEach
    void setUp() {
        service = new StrategyApprovalPersistenceService(
                approvalMapper, new ObjectMapper()
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
                123L, 3L, 1L, option, context, List.of(reviewer)
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
        when(approvalMapper.selectReviewRequests(55L, List.of(7L)))
                .thenReturn(List.of(), List.of(request));

        PreparedStrategyApproval prepared = service.prepare(
                123L, 3L, 1L, option, context, List.of(reviewer)
        );

        assertThat(prepared.finalSelectionId()).isEqualTo(44L);
        assertThat(prepared.strategyOptionId()).isEqualTo(55L);
        assertThat(prepared.reviewRequests()).containsExactly(request);
        verify(approvalMapper).insertReviewRequest(any(ReviewRequestWrite.class));
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
                123L, 3L, 1L, option, context, List.of(reviewer)
        ))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRATEGY_SELECTION_CONFLICT);

        verify(approvalMapper, never()).insertReviewRequest(any());
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
        when(context.strategyCaseId()).thenReturn(123L);
    }

    private static CaseRecord strategyCase() {
        CaseRecord strategyCase = new CaseRecord();
        strategyCase.setStrategyCaseId(123L);
        strategyCase.setSkuId(1001L);
        strategyCase.setCaseName("테스트 Case");
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATED);
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
