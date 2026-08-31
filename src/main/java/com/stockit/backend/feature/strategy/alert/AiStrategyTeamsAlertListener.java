package com.stockit.backend.feature.strategy.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStateChangedEvent;
import com.stockit.backend.feature.strategy.mapper.AiStrategyFailureAlertMapper;
import com.stockit.backend.feature.strategy.vo.AiStrategyFailureAlertVO;

/** 최종 실패 상태 커밋 이후 IT 운영 알림을 독립적인 부가 작업으로 전달합니다. */
@Component
@ConditionalOnProperty(
        prefix = "app.ai-strategy.alert.teams",
        name = "enabled",
        havingValue = "true"
)
class AiStrategyTeamsAlertListener {
    private static final Logger log = LoggerFactory.getLogger(
            AiStrategyTeamsAlertListener.class
    );

    private final AiStrategyFailureAlertMapper mapper;
    private final AiStrategyTeamsAlertMessageFactory messageFactory;
    private final AiStrategyTeamsAlertSender sender;

    AiStrategyTeamsAlertListener(
            AiStrategyFailureAlertMapper mapper,
            AiStrategyTeamsAlertMessageFactory messageFactory,
            AiStrategyTeamsAlertSender sender
    ) {
        this.mapper = mapper;
        this.messageFactory = messageFactory;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationStateChanged(StrategyGenerationStateChangedEvent event) {
        if (event.caseStatus() != StrategyCaseStatus.GENERATION_FAILED) {
            return;
        }
        try {
            AiStrategyFailureAlertVO alert = mapper.selectFailureAlert(
                    event.strategyCaseId()
            );
            if (alert == null) {
                log.error(
                        "AI strategy failure Teams alert source not found. caseId={}",
                        event.strategyCaseId()
                );
                return;
            }
            AiStrategyTeamsAlertMessage message = messageFactory.create(alert);
            sender.send(message);
        } catch (RuntimeException exception) {
            log.error(
                    "AI strategy failure Teams alert delivery failed. caseId={}",
                    event.strategyCaseId(),
                    exception
            );
        }
    }
}
