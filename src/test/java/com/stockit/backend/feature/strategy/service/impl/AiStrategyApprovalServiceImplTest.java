package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.approval.PersistedStrategyApprovalReader;
import com.stockit.backend.feature.strategy.approval.PreparedTeamsDelivery;
import com.stockit.backend.feature.strategy.approval.ResolvedStrategySelection;
import com.stockit.backend.feature.strategy.approval.StrategyApprovalPersistenceService;
import com.stockit.backend.feature.strategy.approval.StrategyReviewStatus;
import com.stockit.backend.feature.strategy.approval.StrategySelectionResolver;
import com.stockit.backend.feature.strategy.approval.StrategyTeamsDeliveryCoordinator;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalCardData;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalProperties;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.DeliveryStatus;
import com.stockit.backend.feature.strategy.service.AiStrategyReviewerService;
import com.stockit.backend.feature.strategy.vo.AiStrategyReviewerVO;

@ExtendWith(MockitoExtension.class)
class AiStrategyApprovalServiceImplTest {

    @Mock private StrategySelectionResolver selectionResolver;
    @Mock private AiStrategyReviewerService reviewerService;
    @Mock private StrategyApprovalPersistenceService persistenceService;
    @Mock private PersistedStrategyApprovalReader approvalReader;
    @Mock private StrategyTeamsDeliveryCoordinator deliveryCoordinator;
    @Mock private ResolvedStrategySelection resolved;
    @Mock private TeamsApprovalCardData cardData;

    private AiStrategyApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiStrategyApprovalServiceImpl(
                selectionResolver,
                reviewerService,
                persistenceService,
                approvalReader,
                deliveryCoordinator,
                new TeamsApprovalProperties()
        );
    }

    @Test
    void delegatesCommittedSelectionToCommonDeliveryCoordinator() {
        AiStrategyReviewerVO reviewer = reviewer(7L, "one@stockit.test");
        PreparedTeamsDelivery prepared = new PreparedTeamsDelivery(
                123L, "CAND-1", 55L, 44L,
                StrategyCaseStatus.GENERATED,
                LocalDate.of(2026, 8, 25), cardData,
                List.of(new com.stockit.backend.feature.strategy.approval.TeamsApprovalRecipient(
                        701L, 7L, "검토자 7", "one@stockit.test",
                        StrategyReviewStatus.PENDING, true
                ))
        );
        AiStrategyTeamsRequestResponse expected = new AiStrategyTeamsRequestResponse(
                123L, "CAND-1", 55L, 44L,
                StrategyCaseStatus.READY_TO_EXECUTE,
                DeliveryStatus.SENT,
                List.of()
        );
        when(selectionResolver.resolve(123L, "CAND-1", null))
                .thenReturn(resolved);
        when(reviewerService.requireReviewers(1L, List.of(7L), 10))
                .thenReturn(List.of(reviewer));
        when(approvalReader.read(123L, 1L)).thenReturn(prepared);
        when(deliveryCoordinator.deliver(prepared, 3L))
                .thenReturn(expected);

        AiStrategyTeamsRequestResponse actual = service.sendToTeams(
                123L, "CAND-1", null, List.of(7L),
                3L, 1L
        );

        assertThat(actual).isSameAs(expected);
        verify(persistenceService).prepare(
                123L, 3L, 1L, resolved, List.of(reviewer)
        );
        verify(approvalReader).read(123L, 1L);
        verify(deliveryCoordinator).deliver(prepared, 3L);
    }

    private static AiStrategyReviewerVO reviewer(Long id, String email) {
        AiStrategyReviewerVO reviewer = new AiStrategyReviewerVO();
        reviewer.setReviewerId(id);
        reviewer.setReviewerName("검토자 " + id);
        reviewer.setEmail(email);
        return reviewer;
    }
}
