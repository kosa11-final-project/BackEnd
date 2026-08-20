package com.stockit.backend.feature.strategy.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * RabbitMQ를 통해 전달하는 AI 전략 생성 작업 식별 메시지
 */
public record StrategyGenerationJobMessage(
        int schemaVersion,
        UUID messageId,
        Long strategyCaseId,
        @JsonFormat(
                pattern = "yyyy-MM-dd'T'HH:mm:ssXXX",
                timezone = "Asia/Seoul"
        )
        OffsetDateTime requestedAt
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static StrategyGenerationJobMessage create(
            Long strategyCaseId,
            OffsetDateTime requestedAt
    ) {
        return new StrategyGenerationJobMessage(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                strategyCaseId,
                requestedAt
        );
    }
}
