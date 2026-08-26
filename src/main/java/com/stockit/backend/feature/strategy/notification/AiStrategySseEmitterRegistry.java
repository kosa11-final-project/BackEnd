package com.stockit.backend.feature.strategy.notification;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 단일 Backend 인스턴스에서 사용자별 복수 브라우저 탭의 SSE 연결을 관리한다.
 *
 * <p>SSE는 생성 작업의 성공 조건이 아닌 best-effort 통지이므로 한 연결의 전송 실패는
 * 해당 연결만 제거하고 다른 연결이나 RabbitMQ Worker로 예외를 전파하지 않는다.</p>
 */
@Component
public class AiStrategySseEmitterRegistry {

    public static final String CONNECTED_EVENT = "connected";
    public static final String PROGRESS_EVENT = "strategy-generation-progress";
    public static final String COMPLETED_EVENT = "strategy-generation-completed";
    public static final String FAILED_EVENT = "strategy-generation-failed";

    private static final Logger log = LoggerFactory.getLogger(
            AiStrategySseEmitterRegistry.class
    );

    private final Map<Long, Map<String, SseConnection>> connections =
            new ConcurrentHashMap<>();
    private final AiStrategySseProperties properties;
    private final AiStrategySseEmitterFactory emitterFactory;

    public AiStrategySseEmitterRegistry(
            AiStrategySseProperties properties,
            AiStrategySseEmitterFactory emitterFactory
    ) {
        this.properties = properties;
        this.emitterFactory = emitterFactory;
    }

    /** 현재 사용자에게 새 연결을 등록하고 브라우저 재동기화용 connected 이벤트를 보낸다. */
    public SseEmitter subscribe(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }

        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.create(
                properties.getTimeout().toMillis()
        );
        SseConnection connection = new SseConnection(connectionId, emitter);

        connections.compute(userId, (key, existing) -> {
            Map<String, SseConnection> values = existing == null
                    ? new ConcurrentHashMap<>()
                    : existing;
            values.put(connectionId, connection);
            return values;
        });

        emitter.onCompletion(() -> remove(userId, connectionId));
        emitter.onTimeout(() -> remove(userId, connectionId));
        emitter.onError(exception -> remove(userId, connectionId));

        try {
            connection.send(SseEmitter.event()
                    .id(connectionId)
                    .name(CONNECTED_EVENT)
                    .reconnectTime(properties.getReconnectTimeMillis())
                    .data(new AiStrategySseConnectedPayload(
                            connectionId,
                            LocalDateTime.now()
                    )));
        } catch (IOException | IllegalStateException exception) {
            remove(userId, connectionId);
            emitter.completeWithError(exception);
            throw new IllegalStateException(
                    "Failed to establish AI strategy SSE connection",
                    exception
            );
        }

        return emitter;
    }

    /** 같은 사용자의 모든 탭에 동일 event id와 Payload를 전송한다. */
    public void broadcast(
            Long userId,
            String eventName,
            AiStrategySseEventPayload payload
    ) {
        if (userId == null || eventName == null || payload == null) {
            return;
        }
        for (SseConnection connection : snapshot(userId)) {
            try {
                connection.send(SseEmitter.event()
                        .id(payload.eventId().toString())
                        .name(eventName)
                        .data(payload));
            } catch (IOException | IllegalStateException exception) {
                log.debug(
                        "Removing failed AI strategy SSE connection. "
                                + "userId={}, connectionId={}",
                        userId,
                        connection.id(),
                        exception
                );
                remove(userId, connection.id());
            }
        }
    }

    /** Proxy가 유휴 연결을 종료하지 않도록 업무 이벤트와 구분되는 SSE comment를 보낸다. */
    @Scheduled(
            initialDelayString = "${app.ai-strategy.sse.heartbeat-interval:25s}",
            fixedDelayString = "${app.ai-strategy.sse.heartbeat-interval:25s}"
    )
    public void sendHeartbeat() {
        connections.forEach((userId, values) -> {
            for (SseConnection connection : List.copyOf(values.values())) {
                try {
                    connection.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException exception) {
                    log.debug(
                            "Removing stale AI strategy SSE connection. "
                                    + "userId={}, connectionId={}",
                            userId,
                            connection.id(),
                            exception
                    );
                    remove(userId, connection.id());
                }
            }
        });
    }

    int connectionCount(Long userId) {
        Map<String, SseConnection> values = connections.get(userId);
        return values == null ? 0 : values.size();
    }

    int totalConnectionCount() {
        return connections.values().stream().mapToInt(Map::size).sum();
    }

    private List<SseConnection> snapshot(Long userId) {
        Map<String, SseConnection> values = connections.get(userId);
        return values == null ? List.of() : List.copyOf(values.values());
    }

    private void remove(Long userId, String connectionId) {
        connections.computeIfPresent(userId, (key, values) -> {
            values.remove(connectionId);
            return values.isEmpty() ? null : values;
        });
    }

    private record SseConnection(String id, SseEmitter emitter) {
        private synchronized void send(SseEmitter.SseEventBuilder event)
                throws IOException {
            emitter.send(event);
        }
    }
}
