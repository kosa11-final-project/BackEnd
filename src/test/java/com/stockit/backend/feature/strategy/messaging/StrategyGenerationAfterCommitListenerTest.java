package com.stockit.backend.feature.strategy.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationRequestedEvent;
import com.stockit.backend.feature.strategy.service.StrategyGenerationFailureService;
import com.stockit.backend.feature.strategy.service.StrategyGenerationJobPublisher;

@ExtendWith(MockitoExtension.class)
class StrategyGenerationAfterCommitListenerTest {

    @Mock
    private StrategyGenerationJobPublisher jobPublisher;

    @Mock
    private StrategyGenerationFailureService failureService;

    @Test
    void isBoundToAfterCommitPhase() throws Exception {
        Method method = StrategyGenerationAfterCommitListener.class.getMethod(
                "publishAfterCommit",
                StrategyGenerationRequestedEvent.class
        );

        TransactionalEventListener annotation = method.getAnnotation(
                TransactionalEventListener.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }

    @Test
    void publishesCaseIdentityWithSeoulOffset() {
        StrategyGenerationAfterCommitListener listener = listener();

        listener.publishAfterCommit(event());

        ArgumentCaptor<StrategyGenerationJobMessage> captor =
                ArgumentCaptor.forClass(StrategyGenerationJobMessage.class);
        verify(jobPublisher).publish(captor.capture());
        assertThat(captor.getValue().strategyCaseId()).isEqualTo(12345L);
        assertThat(captor.getValue().requestedAt().getOffset().getTotalSeconds())
                .isEqualTo(9 * 60 * 60);
        verify(failureService, never()).markFailed(any(), any(), any());
    }

    @Test
    void recordsPublisherFailureInAlreadyCommittedCase() {
        StrategyGenerationAfterCommitListener listener = listener();
        doThrow(new StrategyGenerationPublishException("confirm timeout"))
                .when(jobPublisher).publish(any());

        listener.publishAfterCommit(event());

        verify(failureService).markFailed(
                eq(12345L),
                eq("MQ_PUBLISH_FAILED"),
                eq("confirm timeout")
        );
    }

    private StrategyGenerationAfterCommitListener listener() {
        return new StrategyGenerationAfterCommitListener(
                jobPublisher,
                failureService
        );
    }

    private static StrategyGenerationRequestedEvent event() {
        return new StrategyGenerationRequestedEvent(
                12345L,
                LocalDateTime.of(2026, 8, 20, 14, 30)
        );
    }
}
