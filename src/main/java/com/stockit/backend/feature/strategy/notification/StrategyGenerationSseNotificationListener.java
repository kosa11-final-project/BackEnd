package com.stockit.backend.feature.strategy.notification;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStateChangedEvent;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/** 커밋된 Case 상태만 요청자의 SSE 연결에 best-effort로 전달한다. */
@Component
public class StrategyGenerationSseNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyGenerationSseNotificationListener.class
    );

    private final StrategyCaseMapper strategyCaseMapper;
    private final StrategyNotificationWriter notificationWriter;
    private final AiStrategySseEmitterRegistry emitterRegistry;

    public StrategyGenerationSseNotificationListener(
            StrategyCaseMapper strategyCaseMapper,
            StrategyNotificationWriter notificationWriter,
            AiStrategySseEmitterRegistry emitterRegistry
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.notificationWriter = notificationWriter;
        this.emitterRegistry = emitterRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyRequester(StrategyGenerationStateChangedEvent event) {
        try {
            String eventName = resolveEventName(event);
            if (eventName == null) {
                return;
            }

            StrategyCaseVO strategyCase = strategyCaseMapper.selectStrategyCaseById(
                    event.strategyCaseId()
            );
            if (strategyCase == null || strategyCase.getCreatedBy() == null) {
                log.warn(
                        "AI strategy SSE event skipped because requester could not be "
                                + "resolved. strategyCaseId={}",
                        event.strategyCaseId()
                );
                return;
            }

            AiStrategySseEventPayload payload = new AiStrategySseEventPayload(
                    UUID.randomUUID(),
                    event.strategyCaseId(),
                    strategyCase.getCaseName(),
                    event.caseStatus(),
                    event.generationStage(),
                    LocalDateTime.now()
            );

            persistFinalNotification(event, strategyCase);
            broadcast(strategyCase.getCreatedBy(), eventName, payload);
        } catch (RuntimeException exception) {
            // Case 조회 실패도 이미 커밋된 생성 상태나 RabbitMQ ACK를 되돌리지 않는다.
            log.error(
                    "AI strategy notification handling failed after state commit. "
                            + "strategyCaseId={}, caseStatus={}, generationStage={}",
                    event.strategyCaseId(),
                    event.caseStatus(),
                    event.generationStage(),
                    exception
            );
        }
    }

    private void persistFinalNotification(
            StrategyGenerationStateChangedEvent event,
            StrategyCaseVO strategyCase
    ) {
        if (event.caseStatus() != StrategyCaseStatus.GENERATED
                && event.caseStatus() != StrategyCaseStatus.GENERATION_FAILED) {
            return;
        }
        try {
            notificationWriter.writeFinalNotification(
                    strategyCase.getCreatedBy(),
                    event.strategyCaseId(),
                    strategyCase.getCaseName(),
                    event.caseStatus()
            );
        } catch (RuntimeException exception) {
            // 알림 저장 실패가 실시간 SSE 전송까지 막지 않도록 경계를 분리한다.
            log.error(
                    "AI strategy in-app notification persistence failed. "
                            + "strategyCaseId={}, caseStatus={}",
                    event.strategyCaseId(),
                    event.caseStatus(),
                    exception
            );
        }
    }

    private void broadcast(
            Long requesterId,
            String eventName,
            AiStrategySseEventPayload payload
    ) {
        try {
            emitterRegistry.broadcast(requesterId, eventName, payload);
        } catch (RuntimeException exception) {
            // SSE 실패가 영속 알림이나 생성 작업 결과를 되돌리지 않는다.
            log.error(
                    "AI strategy SSE broadcast failed. strategyCaseId={}",
                    payload.strategyCaseId(),
                    exception
            );
        }
    }

    private static String resolveEventName(
            StrategyGenerationStateChangedEvent event
    ) {
        if (event.caseStatus() == StrategyCaseStatus.GENERATING
                && (event.generationStage() == StrategyGenerationStage.FORECASTING
                || event.generationStage()
                == StrategyGenerationStage.STRATEGY_GENERATING)) {
            // 사용자 Toast가 아니라 목록의 진행선 동기화를 위한 무음 이벤트다.
            return AiStrategySseEmitterRegistry.PROGRESS_EVENT;
        }
        if (event.caseStatus() == StrategyCaseStatus.GENERATED
                && event.generationStage()
                == StrategyGenerationStage.COMPARISON_READY) {
            return AiStrategySseEmitterRegistry.COMPLETED_EVENT;
        }
        if (event.caseStatus() == StrategyCaseStatus.GENERATION_FAILED) {
            return AiStrategySseEmitterRegistry.FAILED_EVENT;
        }
        return null;
    }
}
