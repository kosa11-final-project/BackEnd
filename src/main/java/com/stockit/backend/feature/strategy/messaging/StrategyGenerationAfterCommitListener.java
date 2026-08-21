package com.stockit.backend.feature.strategy.messaging;

import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationRequestedEvent;
import com.stockit.backend.feature.strategy.service.StrategyGenerationFailureService;
import com.stockit.backend.feature.strategy.service.StrategyGenerationJobPublisher;

/**
 * 전략 Case가 커밋된 이후에만 RabbitMQ 작업을 발행하는 트랜잭션 이벤트 Listener
 *
 * <p>저장 롤백 시 유효하지 않은 작업이 큐에 남거나, Worker가 아직 조회할 수 없는
 * Case를 먼저 소비하는 상황을 방지</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "app.ai-strategy.messaging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StrategyGenerationAfterCommitListener {

    private static final Logger log = LoggerFactory.getLogger(
            StrategyGenerationAfterCommitListener.class
    );
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String PUBLISH_FAILURE_CODE = "MQ_PUBLISH_FAILED";

    private final StrategyGenerationJobPublisher jobPublisher;
    private final StrategyGenerationFailureService failureService;

    public StrategyGenerationAfterCommitListener(
            StrategyGenerationJobPublisher jobPublisher,
            StrategyGenerationFailureService failureService
    ) {
        this.jobPublisher = jobPublisher;
        this.failureService = failureService;
    }

    /**
     * 커밋이 보장된 Case 식별자를 영속 메시지로 발행하고 발행 실패를 Case에 기록
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(StrategyGenerationRequestedEvent event) {
        StrategyGenerationJobMessage message = StrategyGenerationJobMessage.create(
                event.strategyCaseId(),
                event.requestedAt().atZone(BUSINESS_ZONE).toOffsetDateTime()
        );
        try {
            jobPublisher.publish(message);
            log.info(
                    "AI strategy generation message published. messageId={}, strategyCaseId={}",
                    message.messageId(),
                    message.strategyCaseId()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "AI strategy generation message publish failed. "
                            + "messageId={}, strategyCaseId={}",
                    message.messageId(),
                    message.strategyCaseId(),
                    exception
            );
            try {
                // 원 요청은 이미 커밋됐으므로 발행 실패도 별도 트랜잭션으로 가시화
                failureService.markFailed(
                        event.strategyCaseId(),
                        PUBLISH_FAILURE_CODE,
                        exception.getMessage()
                );
            } catch (RuntimeException failureRecordException) {
                log.error(
                        "Failed to persist RabbitMQ publish failure. strategyCaseId={}",
                        event.strategyCaseId(),
                        failureRecordException
                );
            }
        }
    }
}
