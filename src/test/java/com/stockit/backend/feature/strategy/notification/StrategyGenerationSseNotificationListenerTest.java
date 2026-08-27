package com.stockit.backend.feature.strategy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStateChangedEvent;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@ExtendWith(MockitoExtension.class)
class StrategyGenerationSseNotificationListenerTest {

    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, 8, 26, 14, 30);
    private static final StrategyDateTimeProvider DATE_TIME_PROVIDER =
            new StrategyDateTimeProvider() {
                @Override
                public LocalDateTime now() {
                    return FIXED_NOW;
                }
            };

    @Mock private StrategyCaseMapper strategyCaseMapper;
    @Mock private StrategyNotificationWriter notificationWriter;
    @Mock private AiStrategySseEmitterRegistry emitterRegistry;

    @Test
    void sendsIntermediateStageAsSilentProgressEvent() {
        when(strategyCaseMapper.selectStrategyCaseById(101L))
                .thenReturn(strategyCase(101L, 7L));

        listener().notifyRequester(new StrategyGenerationStateChangedEvent(
                101L,
                StrategyCaseStatus.GENERATING,
                StrategyGenerationStage.STRATEGY_GENERATING
        ));

        verifyPayload(
                AiStrategySseEmitterRegistry.PROGRESS_EVENT,
                StrategyCaseStatus.GENERATING,
                StrategyGenerationStage.STRATEGY_GENERATING
        );
        verifyNoInteractions(notificationWriter);
    }

    @Test
    void sendsOnlyFinalSuccessAsCompletedNotificationEvent() {
        when(strategyCaseMapper.selectStrategyCaseById(101L))
                .thenReturn(strategyCase(101L, 7L));

        listener().notifyRequester(new StrategyGenerationStateChangedEvent(
                101L,
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY
        ));

        verifyPayload(
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY
        );
        verify(notificationWriter).writeFinalNotification(
                7L,
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATED
        );
    }

    @Test
    void sendsFinalFailureWithoutExposingInternalFailureMessage() {
        when(strategyCaseMapper.selectStrategyCaseById(101L))
                .thenReturn(strategyCase(101L, 7L));

        listener().notifyRequester(new StrategyGenerationStateChangedEvent(
                101L,
                StrategyCaseStatus.GENERATION_FAILED,
                StrategyGenerationStage.FORECASTING
        ));

        verifyPayload(
                AiStrategySseEmitterRegistry.FAILED_EVENT,
                StrategyCaseStatus.GENERATION_FAILED,
                StrategyGenerationStage.FORECASTING
        );
        verify(notificationWriter).writeFinalNotification(
                7L,
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATION_FAILED
        );
    }

    @Test
    void ignoresStatesOutsideGenerationNotificationContract() {
        listener().notifyRequester(new StrategyGenerationStateChangedEvent(
                101L,
                StrategyCaseStatus.READY_TO_EXECUTE,
                StrategyGenerationStage.COMPARISON_READY
        ));

        verifyNoInteractions(strategyCaseMapper, notificationWriter, emitterRegistry);
    }

    @Test
    void neverPropagatesPostCommitNotificationFailureToWorker() {
        when(strategyCaseMapper.selectStrategyCaseById(101L))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> listener().notifyRequester(
                new StrategyGenerationStateChangedEvent(
                        101L,
                        StrategyCaseStatus.GENERATED,
                        StrategyGenerationStage.COMPARISON_READY
                )
        )).doesNotThrowAnyException();
    }

    @Test
    void broadcastsFinalEventEvenWhenPersistentNotificationFails() {
        when(strategyCaseMapper.selectStrategyCaseById(101L))
                .thenReturn(strategyCase(101L, 7L));
        doThrow(new IllegalStateException("notification database unavailable"))
                .when(notificationWriter).writeFinalNotification(
                        7L,
                        101L,
                        "테스트 AI 전략",
                        StrategyCaseStatus.GENERATED
                );

        assertThatCode(() -> listener().notifyRequester(
                new StrategyGenerationStateChangedEvent(
                        101L,
                        StrategyCaseStatus.GENERATED,
                        StrategyGenerationStage.COMPARISON_READY
                )
        )).doesNotThrowAnyException();

        verifyPayload(
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY
        );
    }

    @Test
    void listensOnlyAfterStateTransactionCommits() throws Exception {
        Method method = StrategyGenerationSseNotificationListener.class.getMethod(
                "notifyRequester",
                StrategyGenerationStateChangedEvent.class
        );
        TransactionalEventListener annotation = method.getAnnotation(
                TransactionalEventListener.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    private void verifyPayload(
            String eventName,
            StrategyCaseStatus caseStatus,
            StrategyGenerationStage generationStage
    ) {
        ArgumentCaptor<AiStrategySseEventPayload> captor =
                ArgumentCaptor.forClass(AiStrategySseEventPayload.class);
        verify(emitterRegistry).broadcast(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(eventName),
                captor.capture()
        );
        AiStrategySseEventPayload payload = captor.getValue();
        assertThat(payload.eventId()).isNotNull();
        assertThat(payload.strategyCaseId()).isEqualTo(101L);
        assertThat(payload.caseName()).isEqualTo("테스트 AI 전략");
        assertThat(payload.caseStatus()).isEqualTo(caseStatus);
        assertThat(payload.generationStage()).isEqualTo(generationStage);
        assertThat(payload.occurredAt()).isEqualTo(FIXED_NOW);
    }

    private StrategyGenerationSseNotificationListener listener() {
        return new StrategyGenerationSseNotificationListener(
                strategyCaseMapper,
                notificationWriter,
                emitterRegistry,
                DATE_TIME_PROVIDER
        );
    }

    private static StrategyCaseVO strategyCase(Long strategyCaseId, Long requesterId) {
        StrategyCaseVO value = new StrategyCaseVO();
        value.setStrategyCaseId(strategyCaseId);
        value.setCaseName("테스트 AI 전략");
        value.setCreatedBy(requesterId);
        return value;
    }
}
