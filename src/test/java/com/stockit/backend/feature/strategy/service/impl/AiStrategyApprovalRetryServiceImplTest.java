package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.approval.PersistedStrategyApprovalReader;
import com.stockit.backend.feature.strategy.approval.PreparedTeamsDelivery;
import com.stockit.backend.feature.strategy.approval.StrategyReviewStatus;
import com.stockit.backend.feature.strategy.approval.StrategyTeamsDeliveryCoordinator;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalCardData;
import com.stockit.backend.feature.strategy.approval.TeamsApprovalRecipient;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse.DeliveryStatus;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

@ExtendWith(MockitoExtension.class)
class AiStrategyApprovalRetryServiceImplTest {

    @Mock private PersistedStrategyApprovalReader reader;
    @Mock private StrategyTeamsDeliveryCoordinator coordinator;
    @Mock private StrategyDateTimeProvider dateTimeProvider;
    @Mock private TeamsApprovalCardData cardData;

    @Test
    void retriesPersistedApprovalWithoutAnyRedisDependency() {
        PreparedTeamsDelivery prepared = prepared(
                LocalDate.of(2026, 8, 25), StrategyReviewStatus.FAILED
        );
        AiStrategyTeamsRequestResponse expected = response();
        when(reader.read(123L, 1L)).thenReturn(prepared);
        when(dateTimeProvider.now()).thenReturn(
                LocalDateTime.of(2026, 8, 25, 10, 0)
        );
        when(coordinator.deliver(prepared, 3L)).thenReturn(expected);
        AiStrategyApprovalRetryServiceImpl service = service();

        assertThat(service.retry(123L, 3L, 1L)).isSameAs(expected);
    }

    @Test
    void rejectsUnfinishedDeliveryWhosePlannedStartIsPast() {
        PreparedTeamsDelivery prepared = prepared(
                LocalDate.of(2026, 8, 24), StrategyReviewStatus.FAILED
        );
        when(reader.read(123L, 1L)).thenReturn(prepared);
        when(dateTimeProvider.now()).thenReturn(
                LocalDateTime.of(2026, 8, 25, 10, 0)
        );
        AiStrategyApprovalRetryServiceImpl service = service();

        assertThatThrownBy(() -> service.retry(123L, 3L, 1L))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AI_STRATEGY_PERIOD_STALE);
        verify(coordinator, never()).deliver(prepared, 3L);
    }

    @Test
    void allowsIdempotentReadyResponseAfterPeriodWhenEveryoneWasSent() {
        PreparedTeamsDelivery prepared = prepared(
                LocalDate.of(2026, 8, 24), StrategyReviewStatus.SENT
        );
        AiStrategyTeamsRequestResponse expected = response();
        when(reader.read(123L, 1L)).thenReturn(prepared);
        when(dateTimeProvider.now()).thenReturn(
                LocalDateTime.of(2026, 8, 25, 10, 0)
        );
        when(coordinator.deliver(prepared, 3L)).thenReturn(expected);
        AiStrategyApprovalRetryServiceImpl service = service();

        assertThat(service.retry(123L, 3L, 1L)).isSameAs(expected);
    }

    private AiStrategyApprovalRetryServiceImpl service() {
        return new AiStrategyApprovalRetryServiceImpl(
                reader, coordinator, dateTimeProvider
        );
    }

    private PreparedTeamsDelivery prepared(
            LocalDate startDate,
            StrategyReviewStatus status
    ) {
        return new PreparedTeamsDelivery(
                123L, "CAND-1", 55L, 44L,
                status == StrategyReviewStatus.SENT
                        ? StrategyCaseStatus.READY_TO_EXECUTE
                        : StrategyCaseStatus.GENERATED,
                startDate,
                cardData,
                List.of(new TeamsApprovalRecipient(
                        701L, 7L, "검토자", "reviewer@stockit.test",
                        status, true
                ))
        );
    }

    private static AiStrategyTeamsRequestResponse response() {
        return new AiStrategyTeamsRequestResponse(
                123L, "CAND-1", 55L, 44L,
                StrategyCaseStatus.READY_TO_EXECUTE,
                DeliveryStatus.SENT,
                List.of()
        );
    }
}
