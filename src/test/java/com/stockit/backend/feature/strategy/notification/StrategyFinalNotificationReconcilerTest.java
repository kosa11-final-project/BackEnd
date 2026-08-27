package com.stockit.backend.feature.strategy.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

@ExtendWith(MockitoExtension.class)
class StrategyFinalNotificationReconcilerTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 27, 11, 0);

    @Mock private StrategyNotificationMapper notificationMapper;
    @Mock private StrategyNotificationWriter notificationWriter;
    @Mock private AiStrategySseEmitterRegistry emitterRegistry;

    private StrategyNotificationRecoveryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StrategyNotificationRecoveryProperties();
        properties.setLookback(Duration.ofDays(3));
        properties.setBatchSize(100);
    }

    @Test
    void restoresMissingCompletedAndFailedNotificationsWithinLookback() {
        StrategyNotificationRecoveryCandidate completed = candidate(
                101L,
                7L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        );
        StrategyNotificationRecoveryCandidate failed = candidate(
                102L,
                8L,
                "실패 전략",
                StrategyCaseStatus.GENERATION_FAILED
        );
        when(notificationMapper.selectMissingFinalNotifications(
                NOW.minusDays(3),
                100
        )).thenReturn(List.of(completed, failed));
        when(notificationWriter.writeFinalNotification(
                7L,
                101L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        )).thenReturn(true);
        when(notificationWriter.writeFinalNotification(
                8L,
                102L,
                "실패 전략",
                StrategyCaseStatus.GENERATION_FAILED
        )).thenReturn(true);

        reconciler().reconcile();

        verify(notificationWriter).writeFinalNotification(
                7L,
                101L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        );
        verify(notificationWriter).writeFinalNotification(
                8L,
                102L,
                "실패 전략",
                StrategyCaseStatus.GENERATION_FAILED
        );
        verifyRecoveredPayload(
                7L,
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                101L,
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY
        );
        verifyRecoveredPayload(
                8L,
                AiStrategySseEmitterRegistry.FAILED_EVENT,
                102L,
                StrategyCaseStatus.GENERATION_FAILED,
                StrategyGenerationStage.FORECASTING
        );
    }

    @Test
    void continuesRecoveringOtherCasesWhenOneCandidateFails() {
        StrategyNotificationRecoveryCandidate first = candidate(
                101L,
                7L,
                "첫 전략",
                StrategyCaseStatus.GENERATED
        );
        StrategyNotificationRecoveryCandidate second = candidate(
                102L,
                8L,
                "둘째 전략",
                StrategyCaseStatus.GENERATED
        );
        when(notificationMapper.selectMissingFinalNotifications(any(), eq(100)))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary database failure"))
                .when(notificationWriter).writeFinalNotification(
                        7L,
                        101L,
                        "첫 전략",
                        StrategyCaseStatus.GENERATED
                );
        when(notificationWriter.writeFinalNotification(
                8L,
                102L,
                "둘째 전략",
                StrategyCaseStatus.GENERATED
        )).thenReturn(true);

        reconciler().reconcile();

        verify(notificationWriter).writeFinalNotification(
                8L,
                102L,
                "둘째 전략",
                StrategyCaseStatus.GENERATED
        );
        verifyRecoveredPayload(
                8L,
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                102L,
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY
        );
    }

    @Test
    void stopsCurrentCycleWhenRecoveryQueryFails() {
        when(notificationMapper.selectMissingFinalNotifications(any(), eq(100)))
                .thenThrow(new IllegalStateException("database unavailable"));

        reconciler().reconcile();

        verify(notificationWriter, never()).writeFinalNotification(
                any(),
                any(),
                any(),
                any()
        );
        verifyNoInteractions(emitterRegistry);
    }

    @Test
    void broadcastsOnlyAfterRecoveredNotificationCommitSucceeds() {
        StrategyNotificationRecoveryCandidate completed = candidate(
                101L,
                7L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        );
        when(notificationMapper.selectMissingFinalNotifications(any(), eq(100)))
                .thenReturn(List.of(completed));
        when(notificationWriter.writeFinalNotification(
                7L,
                101L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        )).thenReturn(true);

        reconciler().reconcile();

        InOrder inOrder = inOrder(notificationWriter, emitterRegistry);
        inOrder.verify(notificationWriter).writeFinalNotification(
                7L,
                101L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        );
        inOrder.verify(emitterRegistry).broadcast(
                eq(7L),
                eq(AiStrategySseEmitterRegistry.COMPLETED_EVENT),
                any(AiStrategySseEventPayload.class)
        );
    }

    @Test
    void doesNotBroadcastWhenNotificationWasNotCreated() {
        StrategyNotificationRecoveryCandidate completed = candidate(
                101L,
                7L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        );
        when(notificationMapper.selectMissingFinalNotifications(any(), eq(100)))
                .thenReturn(List.of(completed));
        when(notificationWriter.writeFinalNotification(
                7L,
                101L,
                "완료 전략",
                StrategyCaseStatus.GENERATED
        )).thenReturn(false);

        reconciler().reconcile();

        verifyNoInteractions(emitterRegistry);
    }

    private StrategyFinalNotificationReconciler reconciler() {
        StrategyDateTimeProvider dateTimeProvider =
                new StrategyDateTimeProvider() {
                    @Override
                    public LocalDateTime now() {
                        return NOW;
                    }
                };
        return new StrategyFinalNotificationReconciler(
                notificationMapper,
                notificationWriter,
                emitterRegistry,
                properties,
                dateTimeProvider
        );
    }

    private static StrategyNotificationRecoveryCandidate candidate(
            Long strategyCaseId,
            Long requesterId,
            String caseName,
            StrategyCaseStatus finalStatus
    ) {
        StrategyNotificationRecoveryCandidate value =
                new StrategyNotificationRecoveryCandidate();
        value.setStrategyCaseId(strategyCaseId);
        value.setRequesterId(requesterId);
        value.setCaseName(caseName);
        value.setFinalStatus(finalStatus);
        value.setGenerationStage(finalStatus == StrategyCaseStatus.GENERATED
                ? StrategyGenerationStage.COMPARISON_READY
                : StrategyGenerationStage.FORECASTING);
        return value;
    }

    private void verifyRecoveredPayload(
            Long requesterId,
            String eventName,
            Long strategyCaseId,
            StrategyCaseStatus caseStatus,
            StrategyGenerationStage generationStage
    ) {
        ArgumentCaptor<AiStrategySseEventPayload> captor =
                ArgumentCaptor.forClass(AiStrategySseEventPayload.class);
        verify(emitterRegistry).broadcast(
                eq(requesterId),
                eq(eventName),
                captor.capture()
        );
        AiStrategySseEventPayload payload = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(payload.eventId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(payload.strategyCaseId())
                .isEqualTo(strategyCaseId);
        org.assertj.core.api.Assertions.assertThat(payload.caseStatus())
                .isEqualTo(caseStatus);
        org.assertj.core.api.Assertions.assertThat(payload.generationStage())
                .isEqualTo(generationStage);
        org.assertj.core.api.Assertions.assertThat(payload.occurredAt())
                .isEqualTo(NOW);
    }
}
