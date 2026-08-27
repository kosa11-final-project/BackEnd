package com.stockit.backend.feature.strategy.notification;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

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
    public static final String CONNECTION_REPLACED_EVENT =
            "sse-connection-replaced";

    private static final Logger log = LoggerFactory.getLogger(
            AiStrategySseEmitterRegistry.class
    );

    private final Map<Long, Map<String, SseConnection>> connections =
            new ConcurrentHashMap<>();
    private final AiStrategySseProperties properties;
    private final AiStrategySseEmitterFactory emitterFactory;
    private final StrategyDateTimeProvider dateTimeProvider;
    private final SessionRegistry sessionRegistry;

    public AiStrategySseEmitterRegistry(
            AiStrategySseProperties properties,
            AiStrategySseEmitterFactory emitterFactory,
            StrategyDateTimeProvider dateTimeProvider,
            SessionRegistry sessionRegistry
    ) {
        this.properties = properties;
        this.emitterFactory = emitterFactory;
        this.dateTimeProvider = dateTimeProvider;
        this.sessionRegistry = sessionRegistry;
    }

    /** 현재 사용자에게 새 연결을 등록하고 브라우저 재동기화용 connected 이벤트를 보낸다. */
    public SseEmitter subscribe(
            Long userId,
            String sessionId,
            UUID clientId
    ) {
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }

        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.create(
                properties.getTimeout().toMillis()
        );
        SseConnection connection = new SseConnection(
                connectionId,
                sessionId,
                clientId,
                dateTimeProvider.now(),
                emitter
        );
        List<SseConnection> silentlyReplaced = new ArrayList<>();
        List<SseConnection> limitReplaced = new ArrayList<>();

        connections.compute(userId, (key, existing) -> {
            Map<String, SseConnection> values = existing == null
                    ? new ConcurrentHashMap<>()
                    : existing;
            if (clientId != null) {
                values.values().removeIf(value -> {
                    boolean sameClient = sessionId.equals(value.sessionId())
                            && clientId.equals(value.clientId());
                    if (sameClient) {
                        silentlyReplaced.add(value);
                    }
                    return sameClient;
                });
            }
            values.put(connectionId, connection);
            enforceManagedConnectionLimit(
                    values,
                    sessionId,
                    connectionId,
                    limitReplaced
            );
            return values;
        });

        silentlyReplaced.forEach(this::completeQuietly);
        limitReplaced.forEach(this::completeAsReplaced);

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
                            dateTimeProvider.now()
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

    private void enforceManagedConnectionLimit(
            Map<String, SseConnection> values,
            String sessionId,
            String newConnectionId,
            List<SseConnection> replaced
    ) {
        List<SseConnection> managedConnections = values.values().stream()
                .filter(value -> sessionId.equals(value.sessionId()))
                .filter(value -> value.clientId() != null)
                .sorted(Comparator.comparing(SseConnection::connectedAt)
                        .thenComparing(SseConnection::id))
                .toList();
        int overflow = managedConnections.size()
                - properties.getMaxConnectionsPerSession();
        if (overflow <= 0) {
            return;
        }
        managedConnections.stream()
                .filter(value -> !newConnectionId.equals(value.id()))
                .limit(overflow)
                .forEach(value -> {
                    if (values.remove(value.id(), value)) {
                        replaced.add(value);
                    }
                });
    }

    private void completeAsReplaced(SseConnection connection) {
        try {
            connection.send(SseEmitter.event()
                    .name(CONNECTION_REPLACED_EVENT)
                    .data(new AiStrategySseConnectionControlPayload(
                            "CONNECTION_LIMIT_EXCEEDED",
                            properties.getMaxConnectionsPerSession(),
                            dateTimeProvider.now()
                    )));
        } catch (IOException | IllegalStateException exception) {
            log.debug(
                    "Failed to notify replaced AI strategy SSE connection. "
                            + "connectionId={}",
                    connection.id(),
                    exception
            );
        } finally {
            completeQuietly(connection);
        }
    }

    private void completeQuietly(SseConnection connection) {
        try {
            connection.emitter().complete();
        } catch (RuntimeException exception) {
            log.debug(
                    "Failed to complete AI strategy SSE connection. "
                            + "connectionId={}",
                    connection.id(),
                    exception
            );
        }
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
            if (!isSessionActive(connection.sessionId())) {
                remove(userId, connection.id());
                completeQuietly(connection);
                continue;
            }
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
                if (!isSessionActive(connection.sessionId())) {
                    remove(userId, connection.id());
                    completeQuietly(connection);
                    continue;
                }
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

    int connectionCountBySession(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        return (int) connections.values().stream()
                .flatMap(values -> values.values().stream())
                .filter(value -> sessionId.equals(value.sessionId()))
                .count();
    }

    /** 종료된 HTTP Session에서 만든 모든 연결을 완료하고 제거한다. */
    public void closeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        List<SseConnection> removed = new ArrayList<>();
        connections.forEach((userId, ignored) ->
                connections.computeIfPresent(userId, (key, values) -> {
                    values.values().removeIf(value -> {
                        boolean matches = sessionId.equals(value.sessionId());
                        if (matches) {
                            removed.add(value);
                        }
                        return matches;
                    });
                    return values.isEmpty() ? null : values;
                }));
        removed.forEach(this::completeQuietly);
    }

    private boolean isSessionActive(String sessionId) {
        SessionInformation information =
                sessionRegistry.getSessionInformation(sessionId);
        return information != null && !information.isExpired();
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

    private record SseConnection(
            String id,
            String sessionId,
            UUID clientId,
            LocalDateTime connectedAt,
            SseEmitter emitter
    ) {
        private synchronized void send(SseEmitter.SseEventBuilder event)
                throws IOException {
            emitter.send(event);
        }
    }
}
