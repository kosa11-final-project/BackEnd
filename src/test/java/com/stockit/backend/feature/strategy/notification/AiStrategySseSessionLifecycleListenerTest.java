package com.stockit.backend.feature.strategy.notification;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionDestroyedEvent;

@ExtendWith(MockitoExtension.class)
class AiStrategySseSessionLifecycleListenerTest {

    @Mock private AiStrategySseEmitterRegistry emitterRegistry;
    @Mock private SessionDestroyedEvent event;

    @Test
    void closesConnectionsBelongingToDestroyedSession() {
        org.mockito.Mockito.when(event.getId()).thenReturn("session-1");

        listener().onApplicationEvent(event);

        verify(emitterRegistry).closeSession("session-1");
    }

    private AiStrategySseSessionLifecycleListener listener() {
        return new AiStrategySseSessionLifecycleListener(emitterRegistry);
    }
}
