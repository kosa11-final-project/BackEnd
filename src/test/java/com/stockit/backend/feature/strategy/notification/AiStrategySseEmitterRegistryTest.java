package com.stockit.backend.feature.strategy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

@ExtendWith(MockitoExtension.class)
class AiStrategySseEmitterRegistryTest {

    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2026, 8, 26, 14, 30);
    private static final StrategyDateTimeProvider DATE_TIME_PROVIDER =
            new StrategyDateTimeProvider() {
                @Override
                public LocalDateTime now() {
                    return FIXED_NOW;
                }
            };

    @Mock private AiStrategySseEmitterFactory emitterFactory;
    @Mock private SseEmitter firstEmitter;
    @Mock private SseEmitter secondEmitter;

    private AiStrategySseProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiStrategySseProperties();
        properties.setTimeout(Duration.ofMinutes(30));
        properties.setReconnectTimeMillis(3000L);
    }

    @Test
    void keepsMultipleTabsForOneUserAndIsolatesOtherUsers() throws Exception {
        when(emitterFactory.create(Duration.ofMinutes(30).toMillis()))
                .thenReturn(firstEmitter, secondEmitter);
        AiStrategySseEmitterRegistry registry = registry();

        registry.subscribe(7L);
        registry.subscribe(7L);
        registry.broadcast(
                7L,
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                payload()
        );

        assertThat(registry.connectionCount(7L)).isEqualTo(2);
        assertThat(registry.totalConnectionCount()).isEqualTo(2);
        verify(firstEmitter, times(2)).send(anyEvent());
        verify(secondEmitter, times(2)).send(anyEvent());

        registry.broadcast(
                8L,
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                payload()
        );
        verify(firstEmitter, times(2)).send(anyEvent());
        verify(secondEmitter, times(2)).send(anyEvent());
    }

    @Test
    void removesConnectionWhenEmitterCompletes() throws Exception {
        AtomicReference<Runnable> completion = new AtomicReference<>();
        when(emitterFactory.create(anyLong())).thenReturn(firstEmitter);
        doAnswer(invocation -> {
            completion.set(invocation.getArgument(0));
            return null;
        }).when(firstEmitter).onCompletion(any(Runnable.class));
        AiStrategySseEmitterRegistry registry = registry();

        registry.subscribe(7L);
        completion.get().run();

        assertThat(registry.connectionCount(7L)).isZero();
        assertThat(registry.totalConnectionCount()).isZero();
    }

    @Test
    void removesOnlyFailedConnectionWithoutPropagatingSendFailure()
            throws Exception {
        when(emitterFactory.create(anyLong())).thenReturn(firstEmitter);
        doNothing()
                .doThrow(new IOException("connection closed"))
                .when(firstEmitter).send(anyEvent());
        AiStrategySseEmitterRegistry registry = registry();

        registry.subscribe(7L);
        registry.broadcast(
                7L,
                AiStrategySseEmitterRegistry.FAILED_EVENT,
                payload()
        );

        assertThat(registry.connectionCount(7L)).isZero();
    }

    @Test
    void sendsHeartbeatCommentAndCleansStaleConnection() throws Exception {
        when(emitterFactory.create(anyLong())).thenReturn(firstEmitter);
        doNothing()
                .doThrow(new IllegalStateException("already complete"))
                .when(firstEmitter).send(anyEvent());
        AiStrategySseEmitterRegistry registry = registry();

        registry.subscribe(7L);
        registry.sendHeartbeat();

        assertThat(registry.connectionCount(7L)).isZero();
    }

    @Test
    void doesNotSendWhenPayloadOrRecipientIsMissing() throws Exception {
        when(emitterFactory.create(anyLong())).thenReturn(firstEmitter);
        AiStrategySseEmitterRegistry registry = registry();
        registry.subscribe(7L);

        registry.broadcast(
                null,
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                payload()
        );
        registry.broadcast(7L, null, payload());
        registry.broadcast(
                7L,
                AiStrategySseEmitterRegistry.COMPLETED_EVENT,
                null
        );

        verify(firstEmitter, times(1)).send(anyEvent());
        verify(secondEmitter, never()).send(anyEvent());
    }

    private AiStrategySseEmitterRegistry registry() {
        return new AiStrategySseEmitterRegistry(
                properties,
                emitterFactory,
                DATE_TIME_PROVIDER
        );
    }

    private static AiStrategySseEventPayload payload() {
        return new AiStrategySseEventPayload(
                UUID.randomUUID(),
                101L,
                "테스트 AI 전략",
                StrategyCaseStatus.GENERATED,
                StrategyGenerationStage.COMPARISON_READY,
                LocalDateTime.of(2026, 8, 26, 14, 30)
        );
    }

    private static SseEmitter.SseEventBuilder anyEvent() {
        return any(SseEmitter.SseEventBuilder.class);
    }
}
