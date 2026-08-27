package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyRetryDateAdjustmentPolicy;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.dto.response.RetryAiStrategyGenerationResponse;
import com.stockit.backend.feature.strategy.dto.response.RetryDateAdjustmentRequiredDetails;
import com.stockit.backend.feature.strategy.dto.response.RetryPeriodExpiredDetails;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.AiStrategyGenerationRetryService;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyLotReferenceVO;

@ExtendWith(MockitoExtension.class)
class AiStrategyGenerationRetryServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 14, 30);

    @Mock private StrategyCaseMapper strategyCaseMapper;
    @Mock private StrategyCaseService strategyCaseService;
    @Mock private StrategyDateTimeProvider dateTimeProvider;

    private StrategyCaseRequestPayloadSerializer payloadSerializer;
    private AiStrategyGenerationRetryService retryService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        payloadSerializer = new StrategyCaseRequestPayloadSerializer(objectMapper);
        retryService = new AiStrategyGenerationRetryServiceImpl(
                strategyCaseMapper,
                payloadSerializer,
                strategyCaseService,
                dateTimeProvider
        );
    }

    @Test
    void retriesWithinOneTransaction() throws NoSuchMethodException {
        Transactional transactional = AiStrategyGenerationRetryServiceImpl.class
                .getMethod(
                        "retry",
                        Long.class,
                        StrategyRetryDateAdjustmentPolicy.class,
                        Long.class
                )
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void createsNewCaseWithOriginalUserConditionsAndFreshRequestTime() {
        StrategyCaseVO original = failedCase(
                100L,
                payload(null, null, List.of())
        );
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L)).thenReturn(original);
        when(strategyCaseMapper.selectRetryCaseByParentId(100L)).thenReturn(null);
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(strategyCaseService.createRetryStrategyCase(any(), eq(9L), eq(100L), eq(NOW)))
                .thenReturn(new StrategyCaseCreated(
                        101L,
                        original.getCaseName(),
                        StrategyCaseStatus.GENERATING,
                        null,
                        NOW
                ));

        RetryAiStrategyGenerationResponse response = retryService.retry(
                100L,
                StrategyRetryDateAdjustmentPolicy.REJECT,
                9L
        );

        assertThat(response.strategyCaseId()).isEqualTo(101L);
        assertThat(response.originalStrategyCaseId()).isEqualTo(100L);
        assertThat(response.retryParentStrategyCaseId()).isEqualTo(100L);
        assertThat(response.reusedExistingRetry()).isFalse();
        assertThat(response.dateAdjustment().applied()).isFalse();

        ArgumentCaptor<CreateStrategyCaseCommand> command =
                ArgumentCaptor.forClass(CreateStrategyCaseCommand.class);
        verify(strategyCaseService).createRetryStrategyCase(
                command.capture(), eq(9L), eq(100L), eq(NOW)
        );
        assertThat(command.getValue().caseName()).isEqualTo(original.getCaseName());
        assertThat(command.getValue().skuId()).isEqualTo(1001L);
        assertThat(command.getValue().sourceSalesPointId()).isEqualTo(10L);
        assertThat(command.getValue().candidateSalesPointIds())
                .containsExactly(30L, 20L);
        assertThat(command.getValue().strategyTypes())
                .containsExactly(StrategyType.PRICE_DISCOUNT);
        assertThat(command.getValue().preferredStartDate()).isNull();
        assertThat(command.getValue().preferredEndDate()).isNull();
    }

    @Test
    void requiresConfirmationBeforeAdjustingPassedPreferredStartDate() {
        StrategyCaseVO original = failedCase(
                100L,
                payload(
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 8, 31),
                        List.of()
                )
        );
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L)).thenReturn(original);
        when(strategyCaseMapper.selectRetryCaseByParentId(100L)).thenReturn(null);
        when(dateTimeProvider.now()).thenReturn(NOW);

        assertThatThrownBy(() -> retryService.retry(
                100L,
                StrategyRetryDateAdjustmentPolicy.REJECT,
                9L
        )).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(
                    ErrorCode.AI_STRATEGY_RETRY_DATE_ADJUSTMENT_REQUIRED
            );
            assertThat(exception.getDetails())
                    .isInstanceOfSatisfying(
                            RetryDateAdjustmentRequiredDetails.class,
                            details -> {
                                assertThat(details.originalPreferredStartDate())
                                        .isEqualTo(LocalDate.of(2026, 8, 24));
                                assertThat(details.adjustedPreferredStartDate())
                                        .isEqualTo(LocalDate.of(2026, 8, 27));
                                assertThat(details.adjustedPreferredEndDate())
                                        .isEqualTo(LocalDate.of(2026, 8, 31));
                            }
                    );
        });
        verify(strategyCaseService, never()).createRetryStrategyCase(
                any(), any(), any(), any()
        );
    }

    @Test
    void adjustsOnlyPassedStartDateAfterUserConfirmation() {
        StrategyCaseVO original = failedCase(
                100L,
                payload(
                        LocalDate.of(2026, 8, 24),
                        LocalDate.of(2026, 8, 31),
                        List.of()
                )
        );
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L)).thenReturn(original);
        when(strategyCaseMapper.selectRetryCaseByParentId(100L)).thenReturn(null);
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(strategyCaseService.createRetryStrategyCase(any(), eq(9L), eq(100L), eq(NOW)))
                .thenReturn(new StrategyCaseCreated(
                        101L,
                        original.getCaseName(),
                        StrategyCaseStatus.GENERATING,
                        null,
                        NOW
                ));

        RetryAiStrategyGenerationResponse response = retryService.retry(
                100L,
                StrategyRetryDateAdjustmentPolicy.ADJUST_TO_TODAY,
                9L
        );

        ArgumentCaptor<CreateStrategyCaseCommand> command =
                ArgumentCaptor.forClass(CreateStrategyCaseCommand.class);
        verify(strategyCaseService).createRetryStrategyCase(
                command.capture(), eq(9L), eq(100L), eq(NOW)
        );
        assertThat(command.getValue().preferredStartDate())
                .isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(command.getValue().preferredEndDate())
                .isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(response.dateAdjustment().applied()).isTrue();
    }

    @Test
    void rejectsEntirelyExpiredPreferredPeriodWithoutAutomaticExtension() {
        StrategyCaseVO original = failedCase(
                100L,
                payload(
                        LocalDate.of(2026, 8, 20),
                        LocalDate.of(2026, 8, 26),
                        List.of()
                )
        );
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L)).thenReturn(original);
        when(strategyCaseMapper.selectRetryCaseByParentId(100L)).thenReturn(null);
        when(dateTimeProvider.now()).thenReturn(NOW);

        assertThatThrownBy(() -> retryService.retry(
                100L,
                StrategyRetryDateAdjustmentPolicy.ADJUST_TO_TODAY,
                9L
        )).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AI_STRATEGY_RETRY_PERIOD_EXPIRED);
            assertThat(exception.getDetails()).isInstanceOf(
                    RetryPeriodExpiredDetails.class
            );
        });
        verify(strategyCaseService, never()).createRetryStrategyCase(
                any(), any(), any(), any()
        );
    }

    @Test
    void returnsExistingDirectRetryWithoutPublishingAnotherCreationEvent() {
        StrategyCaseVO original = failedCase(100L, payload(null, null, List.of()));
        StrategyCaseVO existing = new StrategyCaseVO();
        existing.setStrategyCaseId(101L);
        existing.setRetryParentCaseId(100L);
        existing.setCaseName(original.getCaseName());
        existing.setCaseStatus(StrategyCaseStatus.GENERATING);
        existing.setRequestPayloadJson(payload(null, null, List.of()));
        existing.setCreatedAt(NOW);
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L)).thenReturn(original);
        when(strategyCaseMapper.selectRetryCaseByParentId(100L)).thenReturn(existing);

        RetryAiStrategyGenerationResponse response = retryService.retry(
                100L,
                StrategyRetryDateAdjustmentPolicy.REJECT,
                9L
        );

        assertThat(response.strategyCaseId()).isEqualTo(101L);
        assertThat(response.reusedExistingRetry()).isTrue();
        verify(strategyCaseService, never()).createRetryStrategyCase(
                any(), any(), any(), any()
        );
        verify(dateTimeProvider, never()).now();
    }

    @Test
    void rejectsRetryForCaseThatHasNotFinallyFailed() {
        StrategyCaseVO generating = failedCase(100L, payload(null, null, List.of()));
        generating.setCaseStatus(StrategyCaseStatus.GENERATING);
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L))
                .thenReturn(generating);

        assertError(
                () -> retryService.retry(100L, null, 9L),
                ErrorCode.AI_STRATEGY_RETRY_NOT_ALLOWED
        );
    }

    @Test
    void rejectsSelectedLotWhoseSellablePeriodCannotCoverRetry() {
        StrategyCaseVO original = failedCase(
                100L,
                payload(
                        LocalDate.of(2026, 8, 27),
                        LocalDate.of(2026, 8, 31),
                        List.of(501L)
                )
        );
        StrategyLotReferenceVO lot = new StrategyLotReferenceVO();
        lot.setLotId(501L);
        lot.setSkuId(1001L);
        lot.setLotStatus("AVAILABLE");
        lot.setExpiryDate(LocalDate.of(2026, 8, 29));
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L)).thenReturn(original);
        when(strategyCaseMapper.selectRetryCaseByParentId(100L)).thenReturn(null);
        when(strategyCaseMapper.selectLotReferences(List.of(501L)))
                .thenReturn(List.of(lot));
        when(dateTimeProvider.now()).thenReturn(NOW);

        assertError(
                () -> retryService.retry(100L, null, 9L),
                ErrorCode.AI_STRATEGY_RETRY_CONDITIONS_STALE
        );
        verify(strategyCaseService, never()).createRetryStrategyCase(
                any(), any(), any(), any()
        );
    }

    @Test
    void allowsAiToChooseEndDateBeforeLotExpiryWhenPreferredEndIsUnspecified() {
        StrategyCaseVO original = failedCase(
                100L,
                payload(null, null, List.of(501L))
        );
        StrategyLotReferenceVO lot = new StrategyLotReferenceVO();
        lot.setLotId(501L);
        lot.setSkuId(1001L);
        lot.setLotStatus("AVAILABLE");
        lot.setExpiryDate(LocalDate.of(2026, 9, 15));
        when(strategyCaseMapper.selectStrategyCaseByIdForUpdate(100L)).thenReturn(original);
        when(strategyCaseMapper.selectRetryCaseByParentId(100L)).thenReturn(null);
        when(strategyCaseMapper.selectLotReferences(List.of(501L)))
                .thenReturn(List.of(lot));
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(strategyCaseService.createRetryStrategyCase(any(), eq(9L), eq(100L), eq(NOW)))
                .thenReturn(new StrategyCaseCreated(
                        101L,
                        original.getCaseName(),
                        StrategyCaseStatus.GENERATING,
                        null,
                        NOW
                ));

        RetryAiStrategyGenerationResponse response = retryService.retry(
                100L,
                StrategyRetryDateAdjustmentPolicy.REJECT,
                9L
        );

        assertThat(response.strategyCaseId()).isEqualTo(101L);
        ArgumentCaptor<CreateStrategyCaseCommand> command =
                ArgumentCaptor.forClass(CreateStrategyCaseCommand.class);
        verify(strategyCaseService).createRetryStrategyCase(
                command.capture(), eq(9L), eq(100L), eq(NOW)
        );
        assertThat(command.getValue().preferredEndDate()).isNull();
    }

    private StrategyCaseVO failedCase(Long id, String requestPayloadJson) {
        StrategyCaseVO strategyCase = new StrategyCaseVO();
        strategyCase.setStrategyCaseId(id);
        strategyCase.setSkuId(1001L);
        strategyCase.setRequestedSalesPointId(10L);
        strategyCase.setCaseName("테스트 SKU AI 전략");
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATION_FAILED);
        strategyCase.setRequestPayloadJson(requestPayloadJson);
        strategyCase.setCreatedBy(7L);
        return strategyCase;
    }

    private String payload(
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            List<Long> lotIds
    ) {
        return payloadSerializer.serialize(new StrategyCaseRequestPayload(
                lotIds,
                List.of(30L, 20L),
                List.of(StrategyType.PRICE_DISCOUNT),
                preferredStartDate,
                preferredEndDate,
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 11, 21)
        ));
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
