package com.stockit.backend.feature.strategy.calculation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.mapper.StrategyCalculationInputMapper;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationCostVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationInventoryVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPriceVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSalesPointVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSkuVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationWarehouseRouteVO;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.forecast.DailyForecastPrediction;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpoint;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpointStore;
import com.stockit.backend.feature.strategy.forecast.SalesPointForecast;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequest;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequestContext;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequestFactory;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastResponse;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastResponseValidator;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

@ExtendWith(MockitoExtension.class)
class StrategyCalculationContextLoaderImplTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 20);
    private static final LocalDate END = LocalDate.of(2026, 8, 21);

    @Mock
    private StrategyCaseMapper strategyCaseMapper;
    @Mock
    private StrategyCaseRequestPayloadSerializer payloadSerializer;
    @Mock
    private StrategyForecastRequestFactory requestFactory;
    @Mock
    private ForecastCheckpointStore checkpointStore;
    @Mock
    private StrategyForecastResponseValidator responseValidator;
    @Mock
    private StrategyCalculationInputMapper inputMapper;
    @Mock
    private StrategyDateTimeProvider dateTimeProvider;

    private StrategyCalculationContextLoaderImpl loader;

    @BeforeEach
    void setUp() {
        loader = new StrategyCalculationContextLoaderImpl(
                strategyCaseMapper,
                payloadSerializer,
                requestFactory,
                checkpointStore,
                responseValidator,
                inputMapper,
                dateTimeProvider
        );
        lenient().when(inputMapper.selectActiveTransferRoutes())
                .thenReturn(List.of());
        lenient().when(inputMapper.selectTransferCostPolicies(START, END))
                .thenReturn(List.of());
    }

    @Test
    void assemblesSelectedInventoryAndKeepsCandidateWithIncompletePrice() {
        givenCommonCase(10L, List.of(1001L));
        when(inputMapper.selectInventory(101L)).thenReturn(List.of(
                inventory(1L, 1001L, 10L, "20", "3"),
                inventory(2L, 2001L, 20L, "5", "0")
        ));
        when(inputMapper.selectEffectiveCosts(101L, START))
                .thenReturn(List.of(cost("50")));
        when(inputMapper.selectActiveSalesPoints(List.of(10L, 20L)))
                .thenReturn(List.of(salesPoint(10L), salesPoint(20L)));
        StrategyCalculationPriceVO sourcePrice = price(10L, "100", "5", "10");
        StrategyCalculationPriceVO incompleteCandidate = price(20L, "110", "5", null);
        when(inputMapper.selectEffectivePrices(101L, List.of(10L, 20L), START))
                .thenReturn(List.of(sourcePrice, incompleteCandidate));
        when(inputMapper.selectActiveWarehouseRoutes(List.of(10L, 20L)))
                .thenReturn(List.of(route(10L, 501L, 1), route(20L, 501L, 1)));
        when(inputMapper.selectEffectivePolicies(101L, START)).thenReturn(List.of());

        StrategyCalculationContext context = loader.load(12345L);

        assertThat(context.strategyCaseId()).isEqualTo(12345L);
        assertThat(context.evaluationInventory()).singleElement().satisfies(inventory -> {
            assertThat(inventory.lotId()).isEqualTo(1001L);
            assertThat(inventory.availableQty()).isEqualByComparingTo("20");
            assertThat(inventory.reservedQty()).isEqualByComparingTo("3");
        });
        assertThat(context.salesPoints().get(10L).price()).isNotNull();
        assertThat(context.salesPoints().get(10L).existingAvailableQty())
                .isEqualByComparingTo("20");
        assertThat(context.salesPoints().get(20L).price()).isNull();
        assertThat(context.salesPoints().get(20L).currentlyListed()).isTrue();
        assertThat(context.salesPoints().get(20L).existingAvailableQty())
                .isEqualByComparingTo("5");
        assertThat(context.salesPoints().get(20L).warehouseRoutes())
                .extracting(StrategyCalculationContext.WarehouseRoute::warehouseId)
                .containsExactly(501L);
        assertThat(context.requestConstraints().orderedCandidateSalesPointIds())
                .containsExactly(20L);
        assertThat(context.referenceInventory()).hasSize(2);
        assertThat(context.unitCost()).isEqualByComparingTo("50");
        assertThat(context.forecastMetadata().modelVersionId()).isEqualTo(81L);
        verify(checkpointStore).find(12345L, "request-hash", List.of(10L, 20L));
        verify(responseValidator).validate(requestContext(10L), forecastResponse(10L));
    }

    @Test
    void acceptsPublicUnassignedInventoryWithoutSourcePrice() {
        givenCommonCase(null, List.of(1001L));
        StrategyCalculationInventoryVO unassigned = inventory(
                1L,
                1001L,
                null,
                "20",
                "0"
        );
        when(inputMapper.selectInventory(101L)).thenReturn(List.of(unassigned));
        when(inputMapper.selectEffectiveCosts(101L, START))
                .thenReturn(List.of(cost("50")));
        when(inputMapper.selectActiveSalesPoints(List.of(10L, 20L)))
                .thenReturn(List.of(salesPoint(10L), salesPoint(20L)));
        when(inputMapper.selectEffectivePrices(101L, List.of(10L, 20L), START))
                .thenReturn(List.of());
        when(inputMapper.selectActiveWarehouseRoutes(List.of(10L, 20L)))
                .thenReturn(List.of());
        when(inputMapper.selectEffectivePolicies(101L, START)).thenReturn(List.of());

        StrategyCalculationContext context = loader.load(12345L);

        assertThat(context.sourceSalesPointId()).isNull();
        assertThat(context.evaluationInventory()).singleElement()
                .satisfies(inventory -> assertThat(inventory.isPublicUnassigned()).isTrue());
        assertThat(context.salesPoints().values())
                .allSatisfy(salesPoint -> assertThat(salesPoint.price()).isNull());
        assertThat(context.salesPoints().values())
                .allSatisfy(salesPoint -> assertThat(salesPoint.existingAvailableQty())
                        .isEqualByComparingTo("0"));
        assertThat(context.salesPoints().values())
                .allSatisfy(salesPoint -> assertThat(salesPoint.currentlyListed()).isFalse());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void rejectsInvalidStrategyCaseId(Long strategyCaseId) {
        assertThatThrownBy(() -> loader.load(strategyCaseId))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_CASE_ID_INVALID")
                );
    }

    @Test
    void rejectsWhenValidatedForecastCheckpointIsMissing() {
        StrategyCaseVO strategyCase = strategyCase(10L);
        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                List.of(1001L),
                List.of(20L),
                List.of(),
                START,
                END,
                START,
                END
        );
        StrategyForecastRequestContext requestContext = requestContext(10L);
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(strategyCase);
        when(payloadSerializer.deserialize("{}")) .thenReturn(payload);
        when(requestFactory.create(strategyCase, payload)).thenReturn(requestContext);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L, 20L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_FORECAST_NOT_READY")
                );
    }

    @Test
    void rejectsPreferredStrategyPeriodOutsideValidatedForecastRange() {
        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                List.of(1001L),
                List.of(20L),
                List.of(),
                START.minusDays(1),
                null,
                START,
                END
        );
        givenForecastCheckpoint(10L, payload, forecastResponse(10L));

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_STRATEGY_PERIOD_INVALID")
                );
    }

    @Test
    void rejectsReversedPreferredStrategyPeriod() {
        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                List.of(1001L),
                List.of(20L),
                List.of(),
                END,
                START,
                START,
                END
        );
        givenForecastCheckpoint(10L, payload, forecastResponse(10L));

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_STRATEGY_PERIOD_INVALID")
                );
    }

    @Test
    void rejectsWhenEffectiveSkuCostIsMissing() {
        givenCommonCase(10L, List.of(1001L));
        when(inputMapper.selectInventory(101L)).thenReturn(List.of(
                inventory(1L, 1001L, 10L, "20", "0")
        ));
        when(inputMapper.selectEffectiveCosts(101L, START)).thenReturn(List.of());

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_COST_INVALID")
                );
    }

    @Test
    void rejectsOverlappingEffectiveSkuCostsInsteadOfSelectingOneArbitrarily() {
        givenCommonCase(10L, List.of(1001L));
        when(inputMapper.selectInventory(101L)).thenReturn(List.of(
                inventory(1L, 1001L, 10L, "20", "0")
        ));
        when(inputMapper.selectEffectiveCosts(101L, START)).thenReturn(List.of(
                cost("50"),
                cost("55")
        ));

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_COST_INVALID")
                );
    }

    @Test
    void rejectsEmptyEvaluationInventory() {
        givenCommonCase(10L, List.of());
        when(inputMapper.selectInventory(101L)).thenReturn(List.of());

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_INVENTORY_EMPTY")
                );
    }

    @Test
    void rejectsSelectedLotOutsideSourceScope() {
        givenCommonCase(10L, List.of(1001L));
        when(inputMapper.selectInventory(101L)).thenReturn(List.of(
                inventory(1L, 1001L, 20L, "20", "0")
        ));

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_LOT_SCOPE_INVALID")
                );
    }

    @Test
    void rejectsMissingSourceVariableCost() {
        givenCommonCase(10L, List.of(1001L));
        when(inputMapper.selectInventory(101L)).thenReturn(List.of(
                inventory(1L, 1001L, 10L, "20", "0")
        ));
        when(inputMapper.selectEffectiveCosts(101L, START))
                .thenReturn(List.of(cost("50")));
        when(inputMapper.selectActiveSalesPoints(List.of(10L, 20L)))
                .thenReturn(List.of(salesPoint(10L), salesPoint(20L)));
        when(inputMapper.selectEffectivePrices(101L, List.of(10L, 20L), START))
                .thenReturn(List.of(price(10L, "100", null, "10")));

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_SOURCE_PRICE_INVALID")
                );
    }

    @Test
    void rejectsOverlappingSourcePricesInsteadOfSelectingOneArbitrarily() {
        givenCommonCase(10L, List.of(1001L));
        when(inputMapper.selectInventory(101L)).thenReturn(List.of(
                inventory(1L, 1001L, 10L, "20", "0")
        ));
        when(inputMapper.selectEffectiveCosts(101L, START))
                .thenReturn(List.of(cost("50")));
        when(inputMapper.selectActiveSalesPoints(List.of(10L, 20L)))
                .thenReturn(List.of(salesPoint(10L), salesPoint(20L)));
        when(inputMapper.selectEffectivePrices(101L, List.of(10L, 20L), START))
                .thenReturn(List.of(
                        price(10L, "100", "5", "10"),
                        price(10L, "95", "5", "10")
                ));

        assertThatThrownBy(() -> loader.load(12345L))
                .isInstanceOfSatisfying(
                        StrategyCalculationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CALCULATION_SOURCE_PRICE_INVALID")
                );
    }

    private void givenCommonCase(Long sourceSalesPointId, List<Long> lotIds) {
        StrategyCaseRequestPayload payload = new StrategyCaseRequestPayload(
                lotIds,
                List.of(20L),
                List.of(),
                START,
                END,
                START,
                END
        );
        givenForecastCheckpoint(
                sourceSalesPointId,
                payload,
                forecastResponse(sourceSalesPointId)
        );
        when(dateTimeProvider.now()).thenReturn(LocalDateTime.of(2026, 8, 20, 10, 0));
        when(inputMapper.selectActiveSku(101L)).thenReturn(sku());
    }

    private void givenForecastCheckpoint(
            Long sourceSalesPointId,
            StrategyCaseRequestPayload payload,
            StrategyForecastResponse response
    ) {
        StrategyCaseVO strategyCase = strategyCase(sourceSalesPointId);
        StrategyForecastRequestContext requestContext = requestContext(sourceSalesPointId);
        ForecastCheckpoint checkpoint = new ForecastCheckpoint(
                ForecastCheckpoint.CURRENT_SCHEMA_VERSION,
                12345L,
                "request-hash",
                List.of(10L, 20L),
                Instant.parse("2026-08-20T00:00:00Z"),
                81L,
                response
        );
        when(strategyCaseMapper.selectStrategyCaseById(12345L)).thenReturn(strategyCase);
        when(payloadSerializer.deserialize("{}")) .thenReturn(payload);
        when(requestFactory.create(strategyCase, payload)).thenReturn(requestContext);
        when(checkpointStore.find(12345L, "request-hash", List.of(10L, 20L)))
                .thenReturn(Optional.of(checkpoint));
    }

    private static StrategyCaseVO strategyCase(Long sourceSalesPointId) {
        StrategyCaseVO strategyCase = new StrategyCaseVO();
        strategyCase.setStrategyCaseId(12345L);
        strategyCase.setSkuId(101L);
        strategyCase.setRequestedSalesPointId(sourceSalesPointId);
        strategyCase.setCaseStatus(StrategyCaseStatus.GENERATING);
        strategyCase.setGenerationStage(StrategyGenerationStage.STRATEGY_GENERATING);
        strategyCase.setRequestPayloadJson("{}");
        return strategyCase;
    }

    private static StrategyForecastRequestContext requestContext(Long sourceSalesPointId) {
        return new StrategyForecastRequestContext(
                new StrategyForecastRequest(
                        12345L,
                        101L,
                        sourceSalesPointId,
                        List.of(20L),
                        START,
                        END
                ),
                List.of(10L, 20L),
                "request-hash"
        );
    }

    private static StrategyForecastResponse forecastResponse(Long sourceSalesPointId) {
        return new StrategyForecastResponse(
                12345L,
                101L,
                sourceSalesPointId,
                List.of(20L),
                START,
                END,
                2,
                "forecast-run-1",
                "stockit-demand-lightgbm",
                "3",
                OffsetDateTime.of(2026, 8, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9)),
                List.of(
                        forecast(10L, sourceSalesPointId != null && sourceSalesPointId == 10L),
                        forecast(20L, sourceSalesPointId != null && sourceSalesPointId == 20L)
                )
        );
    }

    private static SalesPointForecast forecast(Long salesPointId, boolean source) {
        return new SalesPointForecast(
                salesPointId,
                source,
                List.of(
                        new DailyForecastPrediction(START, decimal("3")),
                        new DailyForecastPrediction(END, decimal("4"))
                )
        );
    }

    private static StrategyCalculationSkuVO sku() {
        StrategyCalculationSkuVO sku = new StrategyCalculationSkuVO();
        sku.setSkuId(101L);
        sku.setSkuCode("SKU-101");
        sku.setSkuName("테스트 SKU");
        sku.setUnitCode("EA");
        sku.setPackageQuantity(BigDecimal.ONE);
        return sku;
    }

    private static StrategyCalculationInventoryVO inventory(
            Long inventoryBalanceId,
            Long lotId,
            Long salesPointId,
            String onHandQty,
            String reservedQty
    ) {
        StrategyCalculationInventoryVO inventory = new StrategyCalculationInventoryVO();
        inventory.setInventoryBalanceId(inventoryBalanceId);
        inventory.setSkuId(101L);
        inventory.setWarehouseId(501L);
        inventory.setStockSalesPointId(salesPointId);
        inventory.setAllocatedSalesPointId(salesPointId);
        inventory.setLotId(lotId);
        inventory.setOnHandQty(decimal(onHandQty));
        inventory.setReservedQty(decimal(reservedQty));
        inventory.setReceivedDate(LocalDate.of(2026, 8, 1));
        inventory.setExpiryDate(LocalDate.of(2026, 9, 1));
        inventory.setLotStatus("AVAILABLE");
        return inventory;
    }

    private static StrategyCalculationSalesPointVO salesPoint(Long salesPointId) {
        StrategyCalculationSalesPointVO salesPoint = new StrategyCalculationSalesPointVO();
        salesPoint.setSalesPointId(salesPointId);
        salesPoint.setSalesPointCode("SP-" + salesPointId);
        salesPoint.setSalesPointName("판매처 " + salesPointId);
        return salesPoint;
    }

    private static StrategyCalculationPriceVO price(
            Long salesPointId,
            String actualPrice,
            String paymentFee,
            String logisticsCost
    ) {
        StrategyCalculationPriceVO price = new StrategyCalculationPriceVO();
        price.setSkuChannelPriceId(salesPointId * 10);
        price.setSalesPointId(salesPointId);
        price.setSellingPrice(decimal("120"));
        price.setActualPrice(decimal(actualPrice));
        price.setMinimumSellingPrice(decimal("70"));
        price.setPaymentFee(paymentFee == null ? null : decimal(paymentFee));
        price.setLogisticsCost(logisticsCost == null ? null : decimal(logisticsCost));
        return price;
    }

    private static StrategyCalculationCostVO cost(String unitCost) {
        StrategyCalculationCostVO cost = new StrategyCalculationCostVO();
        cost.setSkuCostId(1L);
        cost.setUnitCost(decimal(unitCost));
        return cost;
    }

    private static StrategyCalculationWarehouseRouteVO route(
            Long salesPointId,
            Long warehouseId,
            int priorityNo
    ) {
        StrategyCalculationWarehouseRouteVO route =
                new StrategyCalculationWarehouseRouteVO();
        route.setSalesPointWarehouseId(salesPointId * 100 + warehouseId);
        route.setSalesPointId(salesPointId);
        route.setWarehouseId(warehouseId);
        route.setPriorityNo(priorityNo);
        route.setBaseDeliveryCost(decimal("1000"));
        return route;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
