package com.stockit.backend.feature.strategy.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.mapper.StrategyApprovalMapper;

@ExtendWith(MockitoExtension.class)
class StrategyApprovalDeliveryStateServiceTest {

    @Mock private StrategyApprovalMapper approvalMapper;

    @Test
    void claimsDeliveryWithConfiguredLeaseTimeout() {
        StrategyApprovalDeliveryStateService service =
                new StrategyApprovalDeliveryStateService(approvalMapper);
        when(approvalMapper.claimReviewRequest(701L, 3L, 90L))
                .thenReturn(1);

        boolean claimed = service.tryClaim(
                701L, 3L, Duration.ofSeconds(90)
        );

        assertThat(claimed).isTrue();
        verify(approvalMapper).claimReviewRequest(701L, 3L, 90L);
    }

    @Test
    void rejectsClaimWhenAnotherDeliveryIsInProgress() {
        StrategyApprovalDeliveryStateService service =
                new StrategyApprovalDeliveryStateService(approvalMapper);
        when(approvalMapper.claimReviewRequest(701L, 3L, 60L))
                .thenReturn(0);

        assertThat(service.tryClaim(701L, 3L, Duration.ofMinutes(1)))
                .isFalse();
    }
}
