package com.stockit.backend.feature.strategy.notification;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 테스트에서 연결 수명주기를 격리할 수 있도록 SseEmitter 생성을 분리한다. */
@Component
public class AiStrategySseEmitterFactory {

    public SseEmitter create(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }
}
