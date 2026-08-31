package com.stockit.backend.feature.strategy.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.vo.AiStrategyFailureAlertVO;

@ExtendWith(MockitoExtension.class)
class AiStrategyTeamsAlertMessageFactoryTest {
    @Mock
    private StrategyCaseRequestPayloadSerializer payloadSerializer;

    private AiStrategyTeamsAlertMessageFactory factory;

    @BeforeEach
    void setUp() {
        AiStrategyTeamsAlertProperties properties = new AiStrategyTeamsAlertProperties(
                true,
                "https://example.test/webhook",
                null,
                null,
                "production",
                "https://stockit.test/ai-strategies/{strategyCaseId}",
                "https://logs.test/search?caseId={strategyCaseId}"
        );
        factory = new AiStrategyTeamsAlertMessageFactory(
                payloadSerializer,
                new AiStrategyFailureMessageSanitizer(),
                properties
        );
    }

    @Test
    void createsOperationalMessageFromFinalFailure() {
        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                List.of(11L, 12L),
                List.of(20L, 30L),
                List.of(StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 11, 27)
        );
        when(payloadSerializer.deserialize("payload")).thenReturn(payload);

        AiStrategyTeamsAlertMessage message = factory.create(alert());

        assertThat(message.eventType()).isEqualTo("AI_STRATEGY_GENERATION_FAILED");
        assertThat(message.environment()).isEqualTo("production");
        assertThat(message.strategyCaseId()).isEqualTo(123L);
        assertThat(message.failureCategory()).isEqualTo(AiStrategyFailureCategory.GEMINI);
        assertThat(message.failedStage()).isEqualTo("STRATEGY_GENERATING");
        assertThat(message.requestedLotCount()).isEqualTo(2);
        assertThat(message.requestedCandidateSalesPointCount()).isEqualTo(2);
        assertThat(message.salesPointSelectionMode()).isEqualTo("사용자 지정");
        assertThat(message.deduplicationKey())
                .isEqualTo("AI_STRATEGY_GENERATION_FAILED:123");
        assertThat(message.caseUrl())
                .isEqualTo("https://stockit.test/ai-strategies/123");
    }

    @Test
    void stillCreatesAlertWhenStoredRequestPayloadIsInvalid() {
        when(payloadSerializer.deserialize("payload"))
                .thenThrow(new IllegalArgumentException("invalid"));

        AiStrategyTeamsAlertMessage message = factory.create(alert());

        assertThat(message.requestedLotCount()).isNull();
        assertThat(message.salesPointSelectionMode()).isEqualTo("확인 불가");
        assertThat(message.failureMessage()).doesNotContain("secret-value");
    }

    @Test
    void classifiesRetryExhaustionByEmbeddedRootFailureCode() {
        when(payloadSerializer.deserialize("payload"))
                .thenThrow(new IllegalArgumentException("invalid"));
        AiStrategyFailureAlertVO alert = alert();
        alert.setFailureCode("MQ_RETRY_EXHAUSTED");
        alert.setFailureMessage("[LLM_API_TIMEOUT] Gemini timed out");

        AiStrategyTeamsAlertMessage message = factory.create(alert);

        assertThat(message.failureCode()).isEqualTo("MQ_RETRY_EXHAUSTED");
        assertThat(message.rootFailureCode()).isEqualTo("LLM_API_TIMEOUT");
        assertThat(message.failureCategory()).isEqualTo(AiStrategyFailureCategory.GEMINI);
    }

    private static AiStrategyFailureAlertVO alert() {
        AiStrategyFailureAlertVO alert = new AiStrategyFailureAlertVO();
        alert.setStrategyCaseId(123L);
        alert.setCaseCode("CASE-2026-123");
        alert.setCaseName("대체계란 AI 전략");
        alert.setRequestPayloadJson("payload");
        alert.setGenerationStage(StrategyGenerationStage.STRATEGY_GENERATING);
        alert.setFailureCode("LLM_API_UNAVAILABLE");
        alert.setFailureMessage("api_key=secret-value Gemini unavailable");
        alert.setCompletedAt(LocalDateTime.of(2026, 8, 30, 14, 0));
        alert.setRequesterId(7L);
        alert.setRequesterName("이주영");
        alert.setSkuId(1001L);
        alert.setSkuCode("SKU-1001");
        alert.setSkuName("대체계란");
        alert.setSourceSalesPointId(10L);
        alert.setSourceSalesPointName("압구정본점");
        return alert;
    }
}
