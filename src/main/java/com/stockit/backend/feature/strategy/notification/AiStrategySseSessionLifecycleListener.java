package com.stockit.backend.feature.strategy.notification;

import org.springframework.context.ApplicationListener;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.stereotype.Component;

/** 로그아웃·timeout으로 종료된 HTTP Session의 SSE 연결을 즉시 정리한다. */
@Component
public class AiStrategySseSessionLifecycleListener
        implements ApplicationListener<SessionDestroyedEvent> {

    private final AiStrategySseEmitterRegistry emitterRegistry;

    public AiStrategySseSessionLifecycleListener(
            AiStrategySseEmitterRegistry emitterRegistry
    ) {
        this.emitterRegistry = emitterRegistry;
    }

    @Override
    public void onApplicationEvent(SessionDestroyedEvent event) {
        emitterRegistry.closeSession(event.getId());
    }
}
