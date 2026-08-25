package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.approval.PreparedStrategyApproval;
import com.stockit.backend.feature.strategy.approval.ResolvedStrategySelection;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalDeliveryStateService;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalPersistenceService;
import com.stockit.backend.feature.strategy.approval.StrategySelectionResolver;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalRecords.ReviewRequestRecord;
import com.stockit.backend.feature.strategy.approval.StrategyReviewStatus;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalDeliveryException;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalMessage;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalProperties;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalSender;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.DeliveryStatus;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.service.AiStrategyReviewerService;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

@ExtendWith(MockitoExtension.class)
class AiStrategyApprovalServiceImplTest {

    @Mock private StrategySelectionResolver selectionResolver;
    @Mock private AiStrategyReviewerService reviewerService;
    @Mock private StrategyApprovalPersistenceService persistenceService;
    @Mock private StrategyApprovalDeliveryStateService deliveryStateService;
    @Mock private TeamsApprovalSender teamsApprovalSender;
    @Mock private StrategyGenerationResult result;
    @Mock private StrategyGenerationResult.Option option;
    @Mock private StrategyGenerationResult.Candidate candidate;
    @Mock private StrategyCalculationContext context;
    @Mock private ResolvedStrategySelection resolved;
    @Mock private StrategyCalculationContext.Sku sku;

    private AiStrategyApprovalServiceImpl service;
    private AiStrategyReviewerVO first;
    private AiStrategyReviewerVO second;

    @BeforeEach
    void setUp() {
        TeamsApprovalProperties properties = new TeamsApprovalProperties();
        properties.setMaxReviewers(10);
        service = new AiStrategyApprovalServiceImpl(
                selectionResolver,
                reviewerService,
                persistenceService,
                deliveryStateService,
                teamsApprovalSender,
                properties
        );
        first = reviewer(7L, "one@stockit.test");
        second = reviewer(8L, "two@stockit.test");

        when(selectionResolver.resolve(123L, "CAND-1", null))
                .thenReturn(resolved);
        lenient().when(resolved.calculationContext()).thenReturn(context);
        lenient().when(context.sku()).thenReturn(sku);
        lenient().when(sku.skuCode()).thenReturn("SKU-1");
        lenient().when(sku.skuName()).thenReturn("테스트 상품");
        when(reviewerService.requireReviewers(1L, List.of(7L, 8L), 10))
                .thenReturn(List.of(first, second));
    }

    @Test
    void returnsPartialFailureAndKeepsGeneratedStatus() {
        when(persistenceService.prepare(
                123L, 3L, 1L, resolved, List.of(first, second)
        )).thenReturn(prepared());
        when(deliveryStateService.tryClaim(
                701L, 3L, Duration.ofMinutes(1)
        )).thenReturn(true);
        when(deliveryStateService.tryClaim(
                801L, 3L, Duration.ofMinutes(1)
        )).thenReturn(true);
        doThrow(new TeamsApprovalDeliveryException(
                "TEAMS_WEBHOOK_UNAVAILABLE", "failed"
        )).when(teamsApprovalSender).send(any(TeamsApprovalMessage.class));
        when(deliveryStateService.markReadyIfComplete(123L, 55L, 3L))
                .thenReturn(false);

        var response = service.sendToTeams(
                123L, "CAND-1", null, List.of(7L, 8L), 3L, "요청자", 1L
        );

        assertThat(response.caseStatus()).isEqualTo(StrategyCaseStatus.GENERATED);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.reviewers())
                .allMatch(delivery -> delivery.deliveryStatus()
                        == StrategyReviewStatus.FAILED);
        verify(deliveryStateService).markFailed(701L, 3L);
        verify(deliveryStateService).markFailed(801L, 3L);
    }

    @Test
    void skipsAlreadySentReviewerAndMarksCaseReadyWhenComplete() {
        PreparedStrategyApproval prepared = prepared();
        prepared.reviewRequests().get(0).setReviewStatus(StrategyReviewStatus.SENT);
        when(persistenceService.prepare(
                123L, 3L, 1L, resolved, List.of(first, second)
        )).thenReturn(prepared);
        when(deliveryStateService.tryClaim(
                801L, 3L, Duration.ofMinutes(1)
        )).thenReturn(true);
        when(deliveryStateService.markReadyIfComplete(123L, 55L, 3L))
                .thenReturn(true);

        var response = service.sendToTeams(
                123L, "CAND-1", null, List.of(7L, 8L), 3L, "요청자", 1L
        );

        assertThat(response.caseStatus())
                .isEqualTo(StrategyCaseStatus.READY_TO_EXECUTE);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        verify(deliveryStateService).markSent(801L, 3L);
    }

    @Test
    void skipsWebhookWhenAnotherRequestAlreadyClaimedDelivery() {
        when(persistenceService.prepare(
                123L, 3L, 1L, resolved, List.of(first, second)
        )).thenReturn(prepared());
        when(deliveryStateService.tryClaim(
                701L, 3L, Duration.ofMinutes(1)
        )).thenReturn(false);
        when(deliveryStateService.tryClaim(
                801L, 3L, Duration.ofMinutes(1)
        )).thenReturn(false);
        when(deliveryStateService.markReadyIfComplete(123L, 55L, 3L))
                .thenReturn(false);

        var response = service.sendToTeams(
                123L, "CAND-1", null, List.of(7L, 8L), 3L, "요청자", 1L
        );

        assertThat(response.caseStatus()).isEqualTo(StrategyCaseStatus.GENERATED);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(response.reviewers())
                .allMatch(delivery -> delivery.deliveryStatus()
                        == StrategyReviewStatus.SENDING);
        verify(teamsApprovalSender, never()).send(any(TeamsApprovalMessage.class));
    }

    private PreparedStrategyApproval prepared() {
        return new PreparedStrategyApproval(
                123L,
                44L,
                55L,
                StrategyCaseStatus.GENERATED,
                "테스트 Case",
                resolved,
                List.of(request(701L, 7L), request(801L, 8L))
        );
    }

    private static ReviewRequestRecord request(Long requestId, Long reviewerId) {
        ReviewRequestRecord request = new ReviewRequestRecord();
        request.setReviewRequestId(requestId);
        request.setReviewerId(reviewerId);
        request.setReviewStatus(StrategyReviewStatus.PENDING);
        return request;
    }

    private static AiStrategyReviewerVO reviewer(Long id, String email) {
        AiStrategyReviewerVO reviewer = new AiStrategyReviewerVO();
        reviewer.setReviewerId(id);
        reviewer.setReviewerName("검토자 " + id);
        reviewer.setEmail(email);
        return reviewer;
    }
}
