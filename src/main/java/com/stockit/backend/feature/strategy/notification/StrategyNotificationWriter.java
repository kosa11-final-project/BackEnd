package com.stockit.backend.feature.strategy.notification;

import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;

/** 상태 전이 트랜잭션과 분리해 요청자의 최종 생성 결과 알림을 저장한다. */
@Service
public class StrategyNotificationWriter {

    static final String COMPLETED_TYPE = "AI_STRATEGY_GENERATION_COMPLETED";
    static final String FAILED_TYPE = "AI_STRATEGY_GENERATION_FAILED";
    private static final String DEDUPLICATION_CONSTRAINT =
            "UQ_NOTIFICATION_DEDUPE";

    private final StrategyNotificationMapper mapper;

    public StrategyNotificationWriter(StrategyNotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean writeFinalNotification(
            Long requesterId,
            Long strategyCaseId,
            String caseName,
            StrategyCaseStatus caseStatus
    ) {
        NotificationContent content = content(caseName, caseStatus);
        try {
            return mapper.insertIfAbsent(
                    requesterId,
                    strategyCaseId,
                    content.notificationType(),
                    content.severity(),
                    content.title(),
                    content.message(),
                    "AI_STRATEGY:" + strategyCaseId + ":" + caseStatus.name()
            ) == 1;
        } catch (DuplicateKeyException exception) {
            if (isDeduplicationConflict(exception)) {
                return false;
            }
            throw exception;
        }
    }

    private static boolean isDeduplicationConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT)
                    .contains(DEDUPLICATION_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static NotificationContent content(
            String caseName,
            StrategyCaseStatus caseStatus
    ) {
        String displayName = caseName == null || caseName.isBlank()
                ? "AI 전략"
                : caseName.trim();
        if (caseStatus == StrategyCaseStatus.GENERATED) {
            return new NotificationContent(
                    COMPLETED_TYPE,
                    "INFO",
                    "AI 전략 생성 완료",
                    "'" + displayName + "' 생성이 완료되었습니다."
            );
        }
        if (caseStatus == StrategyCaseStatus.GENERATION_FAILED) {
            return new NotificationContent(
                    FAILED_TYPE,
                    "ERROR",
                    "AI 전략 생성 실패",
                    "'" + displayName + "' 생성에 실패했습니다."
            );
        }
        throw new IllegalArgumentException(
                "Only final AI strategy generation status can be notified"
        );
    }

    private record NotificationContent(
            String notificationType,
            String severity,
            String title,
            String message
    ) {
    }
}
