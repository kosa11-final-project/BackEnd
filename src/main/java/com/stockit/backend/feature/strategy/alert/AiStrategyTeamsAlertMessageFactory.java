package com.stockit.backend.feature.strategy.alert;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.vo.AiStrategyFailureAlertVO;

/** 최종 실패 Case와 요청 스냅샷을 운영 알림 메시지로 변환합니다. */
@Component
class AiStrategyTeamsAlertMessageFactory {
    private static final Logger log = LoggerFactory.getLogger(
            AiStrategyTeamsAlertMessageFactory.class
    );
    private static final String EVENT_TYPE = "AI_STRATEGY_GENERATION_FAILED";
    private static final String SEVERITY = "ERROR";
    private static final String AI_AUTO = "AI 자동 선택";
    private static final String USER_SELECTED = "사용자 지정";
    private static final Pattern ROOT_FAILURE_CODE = Pattern.compile(
            "^\\[([A-Z][A-Z0-9_]+)]"
    );

    private final StrategyCaseRequestPayloadSerializer payloadSerializer;
    private final AiStrategyFailureMessageSanitizer sanitizer;
    private final AiStrategyTeamsAlertProperties properties;

    AiStrategyTeamsAlertMessageFactory(
            StrategyCaseRequestPayloadSerializer payloadSerializer,
            AiStrategyFailureMessageSanitizer sanitizer,
            AiStrategyTeamsAlertProperties properties
    ) {
        this.payloadSerializer = payloadSerializer;
        this.sanitizer = sanitizer;
        this.properties = properties;
    }

    AiStrategyTeamsAlertMessage create(AiStrategyFailureAlertVO alert) {
        RequestSummary summary = summarizeRequest(alert);
        String failureCode = alert.getFailureCode();
        String rootFailureCode = resolveRootFailureCode(
                failureCode,
                alert.getFailureMessage()
        );
        return new AiStrategyTeamsAlertMessage(
                EVENT_TYPE,
                SEVERITY,
                properties.resolvedEnvironment(),
                alert.getCompletedAt(),
                alert.getStrategyCaseId(),
                alert.getRetryParentCaseId(),
                alert.getCaseCode(),
                alert.getCaseName(),
                alert.getRequesterId(),
                alert.getRequesterName(),
                alert.getSkuId(),
                alert.getSkuCode(),
                alert.getSkuName(),
                alert.getSourceSalesPointId(),
                alert.getSourceSalesPointName(),
                AiStrategyFailureCategory.from(rootFailureCode),
                resolveFailedStage(alert),
                failureCode,
                rootFailureCode,
                sanitizer.sanitize(alert.getFailureMessage()),
                summary.lotCount(),
                summary.candidateSalesPointCount(),
                summary.strategyTypeCount(),
                summary.salesPointSelectionMode(),
                summary.strategyTypeSelectionMode(),
                summary.preferredStartDate(),
                summary.preferredEndDate(),
                EVENT_TYPE + ":" + alert.getStrategyCaseId(),
                properties.resolveCaseUrl(alert.getStrategyCaseId()),
                properties.resolveLogUrl(alert.getStrategyCaseId())
        );
    }

    private RequestSummary summarizeRequest(AiStrategyFailureAlertVO alert) {
        try {
            StrategyCaseRequestPayload payload = payloadSerializer.deserialize(
                    alert.getRequestPayloadJson()
            );
            return RequestSummary.from(payload);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI strategy failure alert request summary unavailable. caseId={}",
                    alert.getStrategyCaseId()
            );
            return RequestSummary.empty();
        }
    }

    private static String resolveFailedStage(AiStrategyFailureAlertVO alert) {
        if ("MQ_PUBLISH_FAILED".equals(alert.getFailureCode())) {
            return "REQUEST_PUBLISH";
        }
        return alert.getGenerationStage() == null
                ? "UNKNOWN"
                : alert.getGenerationStage().name();
    }

    private static String resolveRootFailureCode(
            String failureCode,
            String failureMessage
    ) {
        if (!"MQ_RETRY_EXHAUSTED".equals(failureCode)
                || failureMessage == null) {
            return failureCode;
        }
        Matcher matcher = ROOT_FAILURE_CODE.matcher(failureMessage.trim());
        return matcher.find() ? matcher.group(1) : failureCode;
    }

    private record RequestSummary(
            Integer lotCount,
            Integer candidateSalesPointCount,
            Integer strategyTypeCount,
            String salesPointSelectionMode,
            String strategyTypeSelectionMode,
            java.time.LocalDate preferredStartDate,
            java.time.LocalDate preferredEndDate
    ) {
        private static RequestSummary from(StrategyCaseRequestPayload payload) {
            List<Long> lotIds = payload.lotIds();
            List<Long> candidateIds = payload.candidateSalesPointIds();
            List<?> strategyTypes = payload.strategyTypes();
            return new RequestSummary(
                    size(lotIds),
                    size(candidateIds),
                    size(strategyTypes),
                    isEmpty(candidateIds) ? AI_AUTO : USER_SELECTED,
                    isEmpty(strategyTypes) ? AI_AUTO : USER_SELECTED,
                    payload.preferredStartDate(),
                    payload.preferredEndDate()
            );
        }

        private static RequestSummary empty() {
            return new RequestSummary(null, null, null, "확인 불가", "확인 불가", null, null);
        }

        private static int size(List<?> values) {
            return values == null ? 0 : values.size();
        }

        private static boolean isEmpty(List<?> values) {
            return values == null || values.isEmpty();
        }
    }
}
