package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
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
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.CreateStrategyCaseCommand;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationRequestedEvent;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.LegacyStrategyCaseCodeGenerator;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.service.StrategyForecastDateRangeResolver;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyLotReferenceVO;
import com.stockit.backend.feature.strategy.vo.StrategySkuReferenceVO;

@ExtendWith(MockitoExtension.class)
class StrategyCaseServiceImplTest {

    private static final LocalDateTime REQUESTED_AT =
            LocalDateTime.of(2026, 8, 17, 14, 30);
    private static final String LEGACY_CASE_CODE =
            "SC-0123456789abcdef0123456789abcdef";

    @Mock
    private StrategyCaseMapper strategyCaseMapper;

    @Mock
    private LegacyStrategyCaseCodeGenerator caseCodeGenerator;

    @Mock
    private StrategyDateTimeProvider dateTimeProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private StrategyCaseService strategyCaseService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        strategyCaseService = new StrategyCaseServiceImpl(
                strategyCaseMapper,
                new StrategyForecastDateRangeResolver(),
                new StrategyCaseRequestPayloadSerializer(objectMapper),
                caseCodeGenerator,
                dateTimeProvider,
                eventPublisher
        );
    }

    @Test
    void createsEachCaseWithinOneTransaction() throws NoSuchMethodException {
        Transactional transactional = StrategyCaseServiceImpl.class
                .getMethod(
                        "createStrategyCase",
                        CreateStrategyCaseCommand.class,
                        Long.class
                )
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void validatesReferencesAndPersistsPrioritizedRequestPayload() throws Exception {
        CreateStrategyCaseCommand command = new CreateStrategyCaseCommand(
                "  여름 재고 전략  ",
                101L,
                10L,
                List.of(1001L, 1002L),
                List.of(30L, 20L),
                List.of(StrategyType.CHANNEL_EXPANSION, StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 27)
        );
        givenActiveSku();
        when(strategyCaseMapper.selectActiveSalesPointIds(anyList()))
                .thenReturn(List.of(10L, 20L, 30L));
        when(strategyCaseMapper.selectLotReferences(command.lotIds()))
                .thenReturn(List.of(
                        lot(1001L, 101L, "AVAILABLE"),
                        lot(1002L, 101L, "AVAILABLE")
                ));
        when(dateTimeProvider.now()).thenReturn(REQUESTED_AT);
        when(caseCodeGenerator.generate()).thenReturn(LEGACY_CASE_CODE);
        mockInsertAndSelect();

        StrategyCaseCreated created = strategyCaseService.createStrategyCase(command, 99L);

        assertThat(created.strategyCaseId()).isEqualTo(777L);
        assertThat(created.caseName()).isEqualTo("여름 재고 전략");
        assertThat(created.caseStatus()).isEqualTo(StrategyCaseStatus.GENERATING);
        assertThat(created.generationStage()).isNull();
        verify(eventPublisher).publishEvent(new StrategyGenerationRequestedEvent(
                777L,
                REQUESTED_AT
        ));

        ArgumentCaptor<StrategyCaseVO> captor = ArgumentCaptor.forClass(StrategyCaseVO.class);
        verify(strategyCaseMapper).insertStrategyCase(captor.capture());
        StrategyCaseVO inserted = captor.getValue();
        assertThat(inserted.getCaseCode()).isEqualTo(LEGACY_CASE_CODE);
        assertThat(inserted.getCreatedBy()).isEqualTo(99L);
        assertThat(inserted.getUpdatedBy()).isEqualTo(99L);

        JsonNode payload = objectMapper.readTree(inserted.getRequestPayloadJson());
        assertThat(payload.get("lotIds").toString()).isEqualTo("[1001,1002]");
        assertThat(payload.get("candidateSalesPointIds").toString()).isEqualTo("[30,20]");
        assertThat(payload.get("strategyTypes").toString())
                .isEqualTo("[\"CHANNEL_EXPANSION\",\"PRICE_DISCOUNT\"]");
        assertThat(payload.get("preferredStartDate").asText()).isEqualTo("2026-08-20");
        assertThat(payload.get("preferredEndDate").asText()).isEqualTo("2026-08-27");
        assertThat(payload.get("forecastStartDate").asText()).isEqualTo("2026-08-17");
        assertThat(payload.get("forecastEndDate").asText()).isEqualTo("2026-08-27");
    }

    @Test
    void createsRetryCaseWithParentAndUsesCallerFixedRequestTime() {
        CreateStrategyCaseCommand command = new CreateStrategyCaseCommand(
                "재시도 전략",
                101L,
                null,
                List.of(),
                List.of(),
                List.of(StrategyType.PRICE_DISCOUNT),
                null,
                null
        );
        givenActiveSku();
        when(caseCodeGenerator.generate()).thenReturn(LEGACY_CASE_CODE);
        mockInsertAndSelect();

        strategyCaseService.createRetryStrategyCase(
                command,
                99L,
                123L,
                REQUESTED_AT
        );

        ArgumentCaptor<StrategyCaseVO> captor = ArgumentCaptor.forClass(
                StrategyCaseVO.class
        );
        verify(strategyCaseMapper).insertStrategyCase(captor.capture());
        assertThat(captor.getValue().getRetryParentCaseId()).isEqualTo(123L);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(99L);
        verify(dateTimeProvider, never()).now();
        verify(eventPublisher).publishEvent(new StrategyGenerationRequestedEvent(
                777L,
                REQUESTED_AT
        ));
    }

    @Test
    void generatesDefaultNameAndNormalizesNullLists() throws Exception {
        CreateStrategyCaseCommand command = new CreateStrategyCaseCommand(
                null,
                101L,
                null,
                null,
                null,
                null,
                null,
                null
        );
        givenActiveSku();
        when(dateTimeProvider.now()).thenReturn(REQUESTED_AT);
        when(caseCodeGenerator.generate()).thenReturn(LEGACY_CASE_CODE);
        mockInsertAndSelect();

        strategyCaseService.createStrategyCase(command, 99L);

        ArgumentCaptor<StrategyCaseVO> captor = ArgumentCaptor.forClass(StrategyCaseVO.class);
        verify(strategyCaseMapper).insertStrategyCase(captor.capture());
        assertThat(captor.getValue().getCaseName())
                .isEqualTo("테스트 SKU AI 전략");
        JsonNode payload = objectMapper.readTree(captor.getValue().getRequestPayloadJson());
        assertThat(payload.get("lotIds").isEmpty()).isTrue();
        assertThat(payload.get("candidateSalesPointIds").isEmpty()).isTrue();
        assertThat(payload.get("strategyTypes").isEmpty()).isTrue();
        assertThat(payload.get("preferredStartDate").isNull()).isTrue();
        assertThat(payload.get("preferredEndDate").isNull()).isTrue();
        assertThat(payload.get("forecastStartDate").asText())
                .isEqualTo(REQUESTED_AT.toLocalDate().toString());
        assertThat(payload.get("forecastEndDate").asText())
                .isEqualTo(REQUESTED_AT.toLocalDate().plusDays(89).toString());
    }

    @Test
    void allowsSourceSalesPointToAlsoBeCandidate() {
        CreateStrategyCaseCommand command = command(
                List.of(),
                List.of(10L, 20L),
                List.of(StrategyType.PRICE_DISCOUNT)
        );
        givenActiveSku();
        when(strategyCaseMapper.selectActiveSalesPointIds(anyList()))
                .thenReturn(List.of(10L, 20L));
        when(dateTimeProvider.now()).thenReturn(REQUESTED_AT);
        when(caseCodeGenerator.generate()).thenReturn(LEGACY_CASE_CODE);
        mockInsertAndSelect();

        strategyCaseService.createStrategyCase(command, 99L);

        verify(strategyCaseMapper).insertStrategyCase(any(StrategyCaseVO.class));
    }

    @Test
    void rejectsDuplicatePrioritizedValuesBeforeReferenceQueries() {
        CreateStrategyCaseCommand command = command(
                List.of(1001L, 1001L),
                List.of(),
                List.of(StrategyType.PRICE_DISCOUNT)
        );

        assertError(
                () -> strategyCaseService.createStrategyCase(command, 99L),
                ErrorCode.AI_STRATEGY_DUPLICATE_INPUT
        );
        verify(strategyCaseMapper, never()).selectActiveSku(any());
    }

    @Test
    void rejectsUnsupportedStrategyType() {
        CreateStrategyCaseCommand command = command(
                List.of(),
                List.of(),
                List.of(StrategyType.REPLENISHMENT_REQUEST)
        );

        assertError(
                () -> strategyCaseService.createStrategyCase(command, 99L),
                ErrorCode.AI_STRATEGY_UNSUPPORTED_TYPE
        );
    }

    @Test
    void rejectsChannelConcentrationUntilItsDemandEffectPolicyIsDefined() {
        CreateStrategyCaseCommand command = command(
                List.of(),
                List.of(),
                List.of(StrategyType.CHANNEL_CONCENTRATION)
        );

        assertError(
                () -> strategyCaseService.createStrategyCase(command, 99L),
                ErrorCode.AI_STRATEGY_UNSUPPORTED_TYPE
        );
    }

    @Test
    void rejectsUnknownSku() {
        CreateStrategyCaseCommand command = command(
                List.of(),
                List.of(),
                List.of(StrategyType.PRICE_DISCOUNT)
        );
        when(strategyCaseMapper.selectActiveSku(101L)).thenReturn(null);

        assertError(
                () -> strategyCaseService.createStrategyCase(command, 99L),
                ErrorCode.AI_STRATEGY_SKU_NOT_FOUND
        );
    }

    @Test
    void rejectsInactiveOrUnknownSalesPoint() {
        CreateStrategyCaseCommand command = command(
                List.of(),
                List.of(20L),
                List.of(StrategyType.PRICE_DISCOUNT)
        );
        givenActiveSku();
        when(strategyCaseMapper.selectActiveSalesPointIds(anyList())).thenReturn(List.of(10L));

        assertError(
                () -> strategyCaseService.createStrategyCase(command, 99L),
                ErrorCode.AI_STRATEGY_SALES_POINT_NOT_FOUND
        );
    }

    @Test
    void distinguishesMissingWrongSkuAndUnavailableLots() {
        givenActiveSku();
        when(strategyCaseMapper.selectActiveSalesPointIds(anyList())).thenReturn(List.of(10L));

        CreateStrategyCaseCommand missing = command(
                List.of(1001L),
                List.of(),
                List.of(StrategyType.PRICE_DISCOUNT)
        );
        when(strategyCaseMapper.selectLotReferences(List.of(1001L))).thenReturn(List.of());
        assertError(
                () -> strategyCaseService.createStrategyCase(missing, 99L),
                ErrorCode.AI_STRATEGY_LOT_NOT_FOUND
        );

        when(strategyCaseMapper.selectLotReferences(List.of(1001L)))
                .thenReturn(List.of(lot(1001L, 102L, "AVAILABLE")));
        assertError(
                () -> strategyCaseService.createStrategyCase(missing, 99L),
                ErrorCode.AI_STRATEGY_LOT_NOT_BELONG_TO_SKU
        );

        when(strategyCaseMapper.selectLotReferences(List.of(1001L)))
                .thenReturn(List.of(lot(1001L, 101L, "EXPIRED")));
        assertError(
                () -> strategyCaseService.createStrategyCase(missing, 99L),
                ErrorCode.AI_STRATEGY_LOT_NOT_FOUND
        );
    }

    private void givenActiveSku() {
        StrategySkuReferenceVO sku = new StrategySkuReferenceVO();
        sku.setSkuId(101L);
        sku.setSkuName("테스트 SKU");
        when(strategyCaseMapper.selectActiveSku(101L)).thenReturn(sku);
    }

    private void mockInsertAndSelect() {
        doAnswer(invocation -> {
            StrategyCaseVO strategyCase = invocation.getArgument(0);
            strategyCase.setStrategyCaseId(777L);
            return null;
        }).when(strategyCaseMapper).insertStrategyCase(any(StrategyCaseVO.class));
        when(strategyCaseMapper.selectStrategyCaseById(777L))
                .thenAnswer(invocation -> {
                    StrategyCaseVO saved = new StrategyCaseVO();
                    saved.setStrategyCaseId(777L);
                    saved.setCaseName("여름 재고 전략");
                    saved.setCaseStatus(StrategyCaseStatus.GENERATING);
                    saved.setCreatedAt(REQUESTED_AT);
                    return saved;
                });
    }

    private static CreateStrategyCaseCommand command(
            List<Long> lotIds,
            List<Long> candidateSalesPointIds,
            List<StrategyType> strategyTypes
    ) {
        return new CreateStrategyCaseCommand(
                "전략 이름",
                101L,
                10L,
                lotIds,
                candidateSalesPointIds,
                strategyTypes,
                null,
                null
        );
    }

    private static StrategyLotReferenceVO lot(Long lotId, Long skuId, String status) {
        StrategyLotReferenceVO lot = new StrategyLotReferenceVO();
        lot.setLotId(lotId);
        lot.setSkuId(skuId);
        lot.setLotStatus(status);
        return lot;
    }

    private static void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
