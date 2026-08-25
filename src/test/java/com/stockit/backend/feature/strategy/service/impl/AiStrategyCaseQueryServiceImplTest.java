package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.calculation.candidate.policy.StrategyPeriodEligibilityPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.BaselineSimulation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCandidateSimulation;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;
import com.stockit.backend.feature.strategy.mapper.AiStrategyCaseDetailMapper;
import com.stockit.backend.feature.strategy.result.InvalidStrategyResultException;
import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;
import com.stockit.backend.feature.strategy.result.StrategyResultStore;
import com.stockit.backend.feature.strategy.result.StrategyResultStoreException;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.simulation.StrategySimulationContextStore;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseDetailVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyLotDisplayVO;
import com.stockit.backend.feature.strategy.vo.AiStrategySalesPointReferenceVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyWarehouseReferenceVO;

@ExtendWith(MockitoExtension.class)
class AiStrategyCaseQueryServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 10, 0);

    @Mock private AiStrategyCaseDetailMapper detailMapper;
    @Mock private StrategyResultStore resultStore;
    @Mock private StrategyCaseRequestPayloadSerializer payloadSerializer;
    @Mock private StrategyDateTimeProvider dateTimeProvider;
    @Mock private StrategySimulationContextStore contextStore;
    @Mock private BaselineSimulation baselineSimulation;
    @Mock private StrategyCandidateSimulation candidateSimulation;

    private AiStrategyCaseQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiStrategyCaseQueryServiceImpl(
                detailMapper,
                resultStore,
                payloadSerializer,
                dateTimeProvider,
                contextStore,
                new StrategyPeriodEligibilityPolicy()
        );
    }

    @Test
    void enrichesHeaderRequestConditionsAndCandidateActionsFromMasterData() {
        AiStrategyCaseDetailVO detail = generatedCase(NOW.plusDays(3));
        StrategyCaseRequestPayload payload = payload();
        when(candidateSimulation.candidateId()).thenReturn("CAND-1");
        StrategyGenerationResult result = result();
        when(detailMapper.selectCaseDetail(123L)).thenReturn(detail);
        when(payloadSerializer.deserialize(detail.getRequestPayloadJson()))
                .thenReturn(payload);
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(resultStore.find(123L)).thenReturn(Optional.of(result));
        when(contextStore.find(123L)).thenReturn(Optional.of(context()));
        when(detailMapper.selectSalesPoints(List.of(10L, 20L)))
                .thenReturn(List.of(salesPoint(10L, "DEPT_MOKDONG", "목동점"),
                        salesPoint(20L, "DEPT_PANGYO", "판교점")));
        when(detailMapper.selectWarehouses(List.of(500L, 600L)))
                .thenReturn(List.of(warehouse(500L, "WH_GYEONGIN", "경인센터"),
                        warehouse(600L, "WH_SUJI", "수지센터")));
        when(detailMapper.selectLots(List.of(501L)))
                .thenReturn(List.of(lot(501L, "LOT-260801-A")));

        AiStrategyCaseResponse response = service.find(123L);

        assertThat(response.sku()).satisfies(sku -> {
            assertThat(sku.skuCode()).isEqualTo("GF-SOUP-MSH-06");
            assertThat(sku.skuName()).isEqualTo("버섯 들깨탕 6팩");
            assertThat(sku.category().categoryName()).isEqualTo("국·탕");
        });
        assertThat(response.requester().userName()).isEqualTo("김영만");
        assertThat(response.requestConditions()).satisfies(conditions -> {
            assertThat(conditions.sourceSalesPoint().salesPointName()).isEqualTo("목동점");
            assertThat(conditions.lots()).singleElement()
                    .extracting(AiStrategyCaseResponse.Lot::lotCode)
                    .isEqualTo("LOT-260801-A");
            assertThat(conditions.candidateSalesPoints()).singleElement()
                    .extracting(AiStrategyCaseResponse.SalesPoint::salesPointName)
                    .isEqualTo("판교점");
            assertThat(conditions.strategyTypes())
                    .containsExactly(StrategyType.RT_TRANSFER, StrategyType.PRICE_DISCOUNT);
            assertThat(conditions.forecastEndDate()).isEqualTo(
                    LocalDate.of(2026, 8, 27)
            );
        });
        assertThat(response.result().options()).singleElement().satisfies(option -> {
            var action = option.candidate().actions().get(0);
            assertThat(action.sourceLocation().locationType().name())
                    .isEqualTo("SALES_POINT");
            assertThat(action.sourceLocation().locationName()).isEqualTo("목동점");
            assertThat(action.targetLocation().locationType().name())
                    .isEqualTo("WAREHOUSE");
            assertThat(action.targetLocation().locationName()).isEqualTo("수지센터");
            assertThat(action.lotAllocations()).singleElement()
                    .satisfies(allocation -> {
                        assertThat(allocation.lotCode()).isEqualTo("LOT-260801-A");
                        assertThat(allocation.quantity()).isEqualByComparingTo("10");
                    });
            assertThat(option.adjustmentConstraints().minimumStartDate())
                    .isEqualTo(LocalDate.of(2026, 8, 24));
            assertThat(option.adjustmentConstraints().latestSelectableEndDate())
                    .isEqualTo(LocalDate.of(2026, 8, 27));
            assertThat(option.adjustmentConstraints().requiresPeriodAdjustment())
                    .isTrue();
            assertThat(option.chartRange().startDate())
                    .isEqualTo(LocalDate.of(2026, 8, 20));
            assertThat(option.chartRange().endDate())
                    .isEqualTo(LocalDate.of(2026, 8, 27));
        });
    }

    @Test
    void returnsGoneWhenGeneratedResultIsMissingFromRedis() {
        AiStrategyCaseDetailVO detail = generatedCase(NOW.plusDays(3));
        when(detailMapper.selectCaseDetail(123L)).thenReturn(detail);
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(resultStore.find(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(123L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_RESULT_EXPIRED)
                );
    }

    @Test
    void returnsGoneWhenRedisResultFailsIntegrityValidation() {
        AiStrategyCaseDetailVO detail = generatedCase(NOW.plusDays(3));
        when(detailMapper.selectCaseDetail(123L)).thenReturn(detail);
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(resultStore.find(123L)).thenThrow(
                new InvalidStrategyResultException("invalid result", null)
        );

        assertThatThrownBy(() -> service.find(123L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_RESULT_EXPIRED)
                );
    }

    @Test
    void keepsRedisAvailabilityFailureAsServerError() {
        AiStrategyCaseDetailVO detail = generatedCase(NOW.plusDays(3));
        StrategyResultStoreException failure = new StrategyResultStoreException(
                "redis unavailable",
                new RuntimeException("connection refused")
        );
        when(detailMapper.selectCaseDetail(123L)).thenReturn(detail);
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(resultStore.find(123L)).thenThrow(failure);

        assertThatThrownBy(() -> service.find(123L)).isSameAs(failure);
    }

    @Test
    void returnsGoneWhenGeneratedCalculationContextIsMissingFromRedis() {
        AiStrategyCaseDetailVO detail = generatedCase(NOW.plusDays(3));
        when(detailMapper.selectCaseDetail(123L)).thenReturn(detail);
        when(payloadSerializer.deserialize(detail.getRequestPayloadJson()))
                .thenReturn(payload());
        when(dateTimeProvider.now()).thenReturn(NOW);
        when(candidateSimulation.candidateId()).thenReturn("CAND-1");
        StrategyGenerationResult storedResult = result();
        when(resultStore.find(123L)).thenReturn(Optional.of(storedResult));
        when(contextStore.find(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(123L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_RESULT_EXPIRED)
                );
    }

    @Test
    void returnsGoneWithoutRedisReadWhenDatabaseExpiryHasPassed() {
        AiStrategyCaseDetailVO detail = generatedCase(NOW);
        when(detailMapper.selectCaseDetail(123L)).thenReturn(detail);
        when(dateTimeProvider.now()).thenReturn(NOW);

        assertThatThrownBy(() -> service.find(123L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_STRATEGY_RESULT_EXPIRED)
                );
        verify(resultStore, never()).find(123L);
    }

    @Test
    void splitsUnboundedLotReferencesAtOracleInExpressionLimit() {
        AiStrategyCaseDetailVO detail = generatedCase(null);
        detail.setCaseStatus(StrategyCaseStatus.GENERATING);
        detail.setGenerationStage(StrategyGenerationStage.FORECASTING);
        detail.setResultCacheKey(null);
        List<Long> lotIds = LongStream.rangeClosed(1, 1_001).boxed().toList();
        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                lotIds, List.of(), List.of(), null, null,
                LocalDate.of(2026, 8, 24), LocalDate.of(2027, 2, 19)
        );
        when(detailMapper.selectCaseDetail(123L)).thenReturn(detail);
        when(payloadSerializer.deserialize(detail.getRequestPayloadJson()))
                .thenReturn(payload);
        when(detailMapper.selectSalesPoints(List.of(10L))).thenReturn(List.of());
        when(detailMapper.selectLots(lotIds.subList(0, 1_000))).thenReturn(List.of());
        when(detailMapper.selectLots(lotIds.subList(1_000, 1_001))).thenReturn(List.of());

        AiStrategyCaseResponse response = service.find(123L);

        assertThat(response.requestConditions().lots()).hasSize(1_001);
        verify(detailMapper).selectLots(lotIds.subList(0, 1_000));
        verify(detailMapper).selectLots(lotIds.subList(1_000, 1_001));
    }

    private StrategyGenerationResult result() {
        StrategyGenerationResult.LotAllocation allocation =
                new StrategyGenerationResult.LotAllocation(
                        9001L, 501L, decimal("10"), 1
                );
        StrategyGenerationResult.Action action = new StrategyGenerationResult.Action(
                StrategyType.RT_TRANSFER,
                500L,
                10L,
                600L,
                null,
                decimal("10"),
                decimal("12000"),
                null,
                null,
                List.of(allocation)
        );
        StrategyGenerationResult.Candidate candidate =
                new StrategyGenerationResult.Candidate(
                        "CAND-1",
                        List.of(StrategyType.RT_TRANSFER),
                        LocalDate.of(2026, 8, 20),
                        null,
                        List.of(action),
                        List.of(),
                        new StrategyGenerationResult.Preference(1, 1, 100),
                        decimal("10")
                );
        StrategyGenerationResult.Option option = new StrategyGenerationResult.Option(
                1, "전략 1", "추천 이유", "장점", "주의사항",
                candidate, candidateSimulation
        );
        return new StrategyGenerationResult(
                StrategyGenerationResult.CURRENT_SCHEMA_VERSION,
                123L,
                NOW.minusMinutes(1),
                baselineSimulation,
                List.of(option),
                null,
                new StrategyGenerationResult.ProviderMetadata(
                        "interaction-1", "gemini", 100, 50
                )
        );
    }

    private static StrategyCaseRequestPayload payload() {
        return new StrategyCaseRequestPayload(
                List.of(501L),
                List.of(20L),
                List.of(StrategyType.RT_TRANSFER, StrategyType.PRICE_DISCOUNT),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 27)
        );
    }

    private static StrategyCalculationContext context() {
        LocalDate start = LocalDate.of(2026, 8, 20);
        LocalDate end = LocalDate.of(2026, 8, 27);
        Map<LocalDate, BigDecimal> forecasts = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            forecasts.put(date, decimal("2"));
        }
        StrategyCalculationContext.Price price =
                new StrategyCalculationContext.Price(
                        1L, decimal("120"), decimal("100"), decimal("70"),
                        decimal("5"), decimal("10")
                );
        StrategyCalculationContext.SalesPoint salesPoint =
                new StrategyCalculationContext.SalesPoint(
                        10L, "DEPT_MOKDONG", "목동점", BigDecimal.ZERO,
                        true, price, forecasts, List.of()
                );
        StrategyCalculationContext.InventoryLot lot =
                new StrategyCalculationContext.InventoryLot(
                        9001L, 501L, 500L, 10L, 10L, decimal("10"),
                        BigDecimal.ZERO, null, start.minusDays(10),
                        end, null, "AVAILABLE"
                );
        return new StrategyCalculationContext(
                123L, 10L, NOW.minusMinutes(2), start, end,
                new StrategyCalculationContext.Sku(
                        6032L, "GF-SOUP-MSH-06", "버섯 들깨탕 6팩",
                        "EA", BigDecimal.ONE
                ),
                decimal("70"),
                new StrategyCalculationContext.RequestConstraints(
                        List.of(20L),
                        List.of(StrategyType.RT_TRANSFER),
                        start,
                        end
                ),
                List.of(lot),
                List.of(lot),
                List.of(),
                Map.of(10L, salesPoint),
                new StrategyCalculationContext.ForecastMetadata(
                        "forecast-1",
                        1L,
                        OffsetDateTime.of(
                                2026, 8, 20, 8, 0, 0, 0,
                                ZoneOffset.ofHours(9)
                        )
                )
        );
    }

    private static AiStrategyCaseDetailVO generatedCase(LocalDateTime expiresAt) {
        AiStrategyCaseDetailVO detail = new AiStrategyCaseDetailVO();
        detail.setStrategyCaseId(123L);
        detail.setSkuId(6032L);
        detail.setSkuCode("GF-SOUP-MSH-06");
        detail.setSkuName("버섯 들깨탕 6팩");
        detail.setImageUrl("https://example.com/mushroom-soup.jpg");
        detail.setCategoryId(301L);
        detail.setCategoryName("국·탕");
        detail.setCategoryLevel(3);
        detail.setRequesterId(7L);
        detail.setRequesterName("김영만");
        detail.setRequestedSalesPointId(10L);
        detail.setCaseName("버섯 들깨탕 수도권 재배치 전략");
        detail.setCaseStatus(StrategyCaseStatus.GENERATED);
        detail.setGenerationStage(StrategyGenerationStage.COMPARISON_READY);
        detail.setRequestPayloadJson("{request-payload}");
        detail.setResultCacheKey("ai-strategy:case:123:result:v1");
        detail.setResultExpiresAt(expiresAt);
        detail.setCreatedAt(NOW.minusMinutes(2));
        detail.setCompletedAt(NOW.minusMinutes(1));
        return detail;
    }

    private static AiStrategySalesPointReferenceVO salesPoint(
            Long id, String code, String name
    ) {
        AiStrategySalesPointReferenceVO value = new AiStrategySalesPointReferenceVO();
        value.setSalesPointId(id);
        value.setSalesPointCode(code);
        value.setSalesPointName(name);
        return value;
    }

    private static AiStrategyWarehouseReferenceVO warehouse(
            Long id, String code, String name
    ) {
        AiStrategyWarehouseReferenceVO value = new AiStrategyWarehouseReferenceVO();
        value.setWarehouseId(id);
        value.setWarehouseCode(code);
        value.setWarehouseName(name);
        return value;
    }

    private static AiStrategyLotDisplayVO lot(Long id, String code) {
        AiStrategyLotDisplayVO value = new AiStrategyLotDisplayVO();
        value.setLotId(id);
        value.setLotCode(code);
        return value;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
