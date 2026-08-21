package com.stockit.backend.feature.strategy.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.StrategyGenerationJobMessage;
import com.stockit.backend.feature.strategy.service.StrategyCasePayloadException;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyGenerationJobHandler;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/**
 * 현재 AI02 범위에서 메시지를 검증하고 Case를 수요예측 단계로 전이
 */
@Service
public class StrategyGenerationJobHandlerImpl implements StrategyGenerationJobHandler {

    private static final String INVALID_MESSAGE_CODE = "MQ_MESSAGE_INVALID";
    private static final String CASE_NOT_FOUND_CODE = "MQ_CASE_NOT_FOUND";
    private static final String PAYLOAD_INVALID_CODE = "MQ_PAYLOAD_INVALID";

    private final StrategyCaseMapper strategyCaseMapper;
    private final StrategyCaseRequestPayloadSerializer payloadSerializer;

    public StrategyGenerationJobHandlerImpl(
            StrategyCaseMapper strategyCaseMapper,
            StrategyCaseRequestPayloadSerializer payloadSerializer
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    @Transactional
    public void handle(StrategyGenerationJobMessage message) {
        validateMessage(message);

        StrategyCaseVO strategyCase = strategyCaseMapper.selectStrategyCaseById(
                message.strategyCaseId()
        );
        if (strategyCase == null) {
            throw new PermanentStrategyGenerationException(
                    CASE_NOT_FOUND_CODE,
                    "AI strategy case does not exist: " + message.strategyCaseId()
            );
        }
        if (isAlreadyHandled(strategyCase)) {
            return;
        }

        validateStoredPayload(strategyCase.getRequestPayloadJson());
        int updated = strategyCaseMapper.markForecastingIfPending(
                message.strategyCaseId()
        );
        if (updated == 1) {
            return;
        }

        StrategyCaseVO latest = strategyCaseMapper.selectStrategyCaseById(
                message.strategyCaseId()
        );
        if (latest == null) {
            throw new PermanentStrategyGenerationException(
                    CASE_NOT_FOUND_CODE,
                    "AI strategy case disappeared during processing: "
                            + message.strategyCaseId()
            );
        }
        if (!isAlreadyHandled(latest)) {
            throw new IllegalStateException(
                    "AI strategy case could not enter FORECASTING: "
                            + message.strategyCaseId()
            );
        }
    }

    private static void validateMessage(StrategyGenerationJobMessage message) {
        if (message == null
                || message.schemaVersion()
                != StrategyGenerationJobMessage.CURRENT_SCHEMA_VERSION
                || message.messageId() == null
                || message.strategyCaseId() == null
                || message.strategyCaseId() <= 0
                || message.requestedAt() == null) {
            throw new PermanentStrategyGenerationException(
                    INVALID_MESSAGE_CODE,
                    "AI strategy generation message contract is invalid"
            );
        }
    }

    private static boolean isAlreadyHandled(StrategyCaseVO strategyCase) {
        return strategyCase.getCaseStatus() != StrategyCaseStatus.GENERATING
                || strategyCase.getGenerationStage() != null;
    }

    private void validateStoredPayload(String payloadJson) {
        try {
            payloadSerializer.deserialize(payloadJson);
        } catch (StrategyCasePayloadException exception) {
            throw new PermanentStrategyGenerationException(
                    PAYLOAD_INVALID_CODE,
                    exception.getMessage(),
                    exception
            );
        }
    }
}
