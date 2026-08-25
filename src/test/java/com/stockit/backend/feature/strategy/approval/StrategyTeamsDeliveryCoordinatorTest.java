package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.DeliveryStatus;

@ExtendWith(MockitoExtension.class)
class StrategyTeamsDeliveryCoordinatorTest {

    @Mock private StrategyApprovalDeliveryStateService stateService;
    @Mock private TeamsApprovalSender sender;
    @Mock private TeamsApprovalCardData cardData;

    private StrategyTeamsDeliveryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        TeamsApprovalProperties properties = new TeamsApprovalProperties();
        properties.setClaimTimeout(Duration.ofMinutes(1));
        coordinator = new StrategyTeamsDeliveryCoordinator(
                stateService, sender, properties
        );
    }

    @Test
    void skipsSentReviewerAndSendsOnlyClaimedRecipient() {
        PreparedTeamsDelivery prepared = prepared(List.of(
                recipient(701L, 7L, StrategyReviewStatus.SENT),
                recipient(801L, 8L, StrategyReviewStatus.FAILED)
        ));
        when(stateService.tryClaim(801L, 3L, Duration.ofMinutes(1)))
                .thenReturn(true);
        when(stateService.markSent(801L, 3L)).thenReturn(StrategyReviewStatus.SENT);
        when(stateService.markReadyIfComplete(123L, 55L, 3L)).thenReturn(true);

        var response = coordinator.deliver(prepared, 3L);

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(response.caseStatus()).isEqualTo(StrategyCaseStatus.READY_TO_EXECUTE);
        ArgumentCaptor<TeamsApprovalMessage> message =
                ArgumentCaptor.forClass(TeamsApprovalMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().deliveryKey())
                .isEqualTo("AI_STRATEGY_REVIEW:801");
        assertThat(message.getValue().cardData()).isSameAs(cardData);
    }

    @Test
    void recordsPartialFailureWithoutResendingSuccessfulReviewer() {
        PreparedTeamsDelivery prepared = prepared(List.of(
                recipient(701L, 7L, StrategyReviewStatus.SENT),
                recipient(801L, 8L, StrategyReviewStatus.FAILED)
        ));
        when(stateService.tryClaim(801L, 3L, Duration.ofMinutes(1)))
                .thenReturn(true);
        doThrow(new TeamsApprovalDeliveryException(
                "TEAMS_WEBHOOK_TIMEOUT", "timeout"
        )).when(sender).send(any(TeamsApprovalMessage.class));
        when(stateService.markFailed(801L, 3L))
                .thenReturn(StrategyReviewStatus.FAILED);
        when(stateService.markReadyIfComplete(123L, 55L, 3L)).thenReturn(false);

        var response = coordinator.deliver(prepared, 3L);

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.PARTIAL_FAILED);
        assertThat(response.caseStatus()).isEqualTo(StrategyCaseStatus.GENERATED);
        assertThat(response.reviewers().get(1).failureCode())
                .isEqualTo("TEAMS_WEBHOOK_TIMEOUT");
    }

    @Test
    void usesCurrentStatusWhenClaimOrCompletionWasLost() {
        PreparedTeamsDelivery prepared = prepared(List.of(
                recipient(701L, 7L, StrategyReviewStatus.FAILED)
        ));
        when(stateService.tryClaim(701L, 3L, Duration.ofMinutes(1)))
                .thenReturn(false);
        when(stateService.currentStatus(701L)).thenReturn(StrategyReviewStatus.SENDING);
        when(stateService.markReadyIfComplete(123L, 55L, 3L)).thenReturn(false);

        var response = coordinator.deliver(prepared, 3L);

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        verify(sender, never()).send(any());
    }

    @Test
    void marksUnavailableReviewerFailedWithoutCallingWebhook() {
        TeamsApprovalRecipient unavailable = new TeamsApprovalRecipient(
                701L, 7L, "검토자", null,
                StrategyReviewStatus.PENDING, false
        );
        when(stateService.tryClaim(701L, 3L, Duration.ofMinutes(1)))
                .thenReturn(true);
        when(stateService.markFailed(701L, 3L))
                .thenReturn(StrategyReviewStatus.FAILED);
        when(stateService.markReadyIfComplete(123L, 55L, 3L)).thenReturn(false);

        var response = coordinator.deliver(prepared(List.of(unavailable)), 3L);

        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(response.reviewers().get(0).failureCode())
                .isEqualTo("AI_STRATEGY_REVIEWER_NOT_FOUND");
        verify(sender, never()).send(any());
    }

    private PreparedTeamsDelivery prepared(List<TeamsApprovalRecipient> recipients) {
        return new PreparedTeamsDelivery(
                123L, "CAND-1", 55L, 44L,
                StrategyCaseStatus.GENERATED,
                LocalDate.of(2026, 8, 25),
                cardData,
                recipients
        );
    }

    private static TeamsApprovalRecipient recipient(
            Long requestId,
            Long reviewerId,
            StrategyReviewStatus status
    ) {
        return new TeamsApprovalRecipient(
                requestId, reviewerId, "검토자 " + reviewerId,
                reviewerId + "@stockit.test", status, true
        );
    }
}
