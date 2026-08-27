package com.stockit.backend.feature.strategy.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

/** 최종 상태 커밋 뒤 유실된 완료·실패 인앱 알림을 멱등하게 복구한다. */
@Component
@ConditionalOnProperty(
        prefix = "app.ai-strategy.notification-recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StrategyFinalNotificationReconciler {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyFinalNotificationReconciler.class
    );

    private final StrategyNotificationMapper notificationMapper;
    private final StrategyNotificationWriter notificationWriter;
    private final AiStrategySseEmitterRegistry emitterRegistry;
    private final StrategyNotificationRecoveryProperties properties;
    private final StrategyDateTimeProvider dateTimeProvider;

    public StrategyFinalNotificationReconciler(
            StrategyNotificationMapper notificationMapper,
            StrategyNotificationWriter notificationWriter,
            AiStrategySseEmitterRegistry emitterRegistry,
            StrategyNotificationRecoveryProperties properties,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.notificationMapper = notificationMapper;
        this.notificationWriter = notificationWriter;
        this.emitterRegistry = emitterRegistry;
        this.properties = properties;
        this.dateTimeProvider = dateTimeProvider;
    }

    @Scheduled(
            initialDelayString = "${app.ai-strategy.notification-recovery.initial-delay:30s}",
            fixedDelayString = "${app.ai-strategy.notification-recovery.fixed-delay:60s}"
    )
    public void reconcile() {
        LocalDateTime completedFrom = dateTimeProvider.now()
                .minus(properties.getLookback());
        List<StrategyNotificationRecoveryCandidate> candidates;
        try {
            candidates = notificationMapper.selectMissingFinalNotifications(
                    completedFrom,
                    properties.getBatchSize()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "AI strategy final notification recovery query failed. "
                            + "completedFrom={}",
                    completedFrom,
                    exception
            );
            return;
        }

        int createdCount = 0;
        for (StrategyNotificationRecoveryCandidate candidate : candidates) {
            try {
                boolean created = notificationWriter.writeFinalNotification(
                        candidate.getRequesterId(),
                        candidate.getStrategyCaseId(),
                        candidate.getCaseName(),
                        candidate.getFinalStatus()
                );
                if (created) {
                    createdCount++;
                    broadcastRecoveredFinalEvent(candidate);
                }
            } catch (RuntimeException exception) {
                log.error(
                        "AI strategy final notification recovery failed. "
                                + "strategyCaseId={}, finalStatus={}",
                        candidate.getStrategyCaseId(),
                        candidate.getFinalStatus(),
                        exception
                );
            }
        }

        if (!candidates.isEmpty()) {
            log.info(
                    "AI strategy final notification recovery completed. "
                            + "candidateCount={}, createdCount={}",
                    candidates.size(),
                    createdCount
            );
        }
    }

    private void broadcastRecoveredFinalEvent(
            StrategyNotificationRecoveryCandidate candidate
    ) {
        String eventName = candidate.getFinalStatus()
                == StrategyCaseStatus.GENERATED
                ? AiStrategySseEmitterRegistry.COMPLETED_EVENT
                : AiStrategySseEmitterRegistry.FAILED_EVENT;
        AiStrategySseEventPayload payload = new AiStrategySseEventPayload(
                UUID.randomUUID(),
                candidate.getStrategyCaseId(),
                candidate.getCaseName(),
                candidate.getFinalStatus(),
                candidate.getGenerationStage(),
                dateTimeProvider.now()
        );
        try {
            emitterRegistry.broadcast(
                    candidate.getRequesterId(),
                    eventName,
                    payload
            );
        } catch (RuntimeException exception) {
            // 알림은 이미 커밋됐으므로 SSE 실패로 복구 결과를 되돌리지 않는다.
            log.error(
                    "AI strategy recovered notification SSE broadcast failed. "
                            + "strategyCaseId={}, finalStatus={}",
                    candidate.getStrategyCaseId(),
                    candidate.getFinalStatus(),
                    exception
            );
        }
    }
}
