package com.stockit.backend.feature.strategy.calculation.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationException;
import com.stockit.backend.feature.strategy.calculation.mapper.StrategyCalculationInputMapper;
import com.stockit.backend.feature.strategy.calculation.service.StrategyCalculationContextLoader;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationCostVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationInventoryVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPolicyVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPriceVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSalesPointVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSkuVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferCostPolicyVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferRouteVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationWarehouseRouteVO;
import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.dto.StrategyCaseRequestPayload;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpoint;
import com.stockit.backend.feature.strategy.forecast.ForecastCheckpointStore;
import com.stockit.backend.feature.strategy.forecast.SalesPointForecast;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequestContext;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastRequestFactory;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastResponse;
import com.stockit.backend.feature.strategy.forecast.StrategyForecastResponseValidator;
import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;
import com.stockit.backend.feature.strategy.service.StrategyCaseRequestPayloadSerializer;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;
import com.stockit.backend.feature.strategy.vo.StrategyCaseVO;

/**
 * 전략 Case, Redis 예측 체크포인트와 계산 시점 DB 데이터를 조립한다.
 *
 * <p>후보 판매처의 가격 누락은 해당 판매처만 후속 후보 계산에서 제외할 수 있도록
 * {@code price=null}로 보존하지만, 기준 판매처 가격과 SKU 원가는 필수로 검증한다.
 * 같은 시점에 유효한 기준 가격이나 원가가 중복되면 임의의 최신 행을 선택하지 않고
 * 데이터 무결성 오류로 처리한다.</p>
 */
@Service
public class StrategyCalculationContextLoaderImpl
        implements StrategyCalculationContextLoader {

    private final StrategyCaseMapper strategyCaseMapper;
    private final StrategyCaseRequestPayloadSerializer payloadSerializer;
    private final StrategyForecastRequestFactory requestFactory;
    private final ForecastCheckpointStore checkpointStore;
    private final StrategyForecastResponseValidator responseValidator;
    private final StrategyCalculationInputMapper inputMapper;
    private final StrategyDateTimeProvider dateTimeProvider;

    public StrategyCalculationContextLoaderImpl(
            StrategyCaseMapper strategyCaseMapper,
            StrategyCaseRequestPayloadSerializer payloadSerializer,
            StrategyForecastRequestFactory requestFactory,
            ForecastCheckpointStore checkpointStore,
            StrategyForecastResponseValidator responseValidator,
            StrategyCalculationInputMapper inputMapper,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.strategyCaseMapper = strategyCaseMapper;
        this.payloadSerializer = payloadSerializer;
        this.requestFactory = requestFactory;
        this.checkpointStore = checkpointStore;
        this.responseValidator = responseValidator;
        this.inputMapper = inputMapper;
        this.dateTimeProvider = dateTimeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public StrategyCalculationContext load(Long strategyCaseId) {
        StrategyCaseVO strategyCase = loadStrategyCase(strategyCaseId);
        StrategyCaseRequestPayload payload = payloadSerializer.deserialize(
                strategyCase.getRequestPayloadJson()
        );
        StrategyForecastRequestContext forecastContext = requestFactory.create(
                strategyCase,
                payload
        );
        ForecastCheckpoint checkpoint = checkpointStore.find(
                strategyCaseId,
                forecastContext.requestHash(),
                forecastContext.expectedSalesPointIds()
        ).orElseThrow(() -> failure(
                "CALCULATION_FORECAST_NOT_READY",
                "Validated demand forecast checkpoint does not exist"
        ));
        responseValidator.validate(forecastContext, checkpoint.forecastResponse());
        validateStrategyPeriod(payload, checkpoint.forecastResponse());

        LocalDateTime calculatedAt = dateTimeProvider.now();
        return assemble(
                strategyCase,
                payload,
                checkpoint.forecastResponse(),
                checkpoint.modelVersionId(),
                forecastContext.expectedSalesPointIds(),
                forecastContext.requestHash(),
                calculatedAt
        );
    }

    private static void validateStrategyPeriod(
            StrategyCaseRequestPayload payload,
            StrategyForecastResponse forecast
    ) {
        if (forecast == null
                || forecast.forecastStartDate() == null
                || forecast.forecastEndDate() == null) {
            throw failure(
                    "CALCULATION_STRATEGY_PERIOD_INVALID",
                    "Validated forecast range is missing"
            );
        }
        LocalDate strategyStartDate = payload.preferredStartDate() != null
                ? payload.preferredStartDate()
                : forecast.forecastStartDate();
        LocalDate strategyEndDate = payload.preferredEndDate() != null
                ? payload.preferredEndDate()
                : forecast.forecastEndDate();
        if (strategyStartDate == null
                || strategyEndDate == null
                || strategyStartDate.isBefore(forecast.forecastStartDate())
                || strategyEndDate.isAfter(forecast.forecastEndDate())
                || strategyStartDate.isAfter(strategyEndDate)) {
            throw failure(
                    "CALCULATION_STRATEGY_PERIOD_INVALID",
                    "Preferred strategy period must be within the validated forecast range"
            );
        }
    }

    private StrategyCaseVO loadStrategyCase(Long strategyCaseId) {
        if (strategyCaseId == null || strategyCaseId <= 0) {
            throw failure(
                    "CALCULATION_CASE_ID_INVALID",
                    "strategyCaseId must be positive"
            );
        }
        StrategyCaseVO strategyCase = strategyCaseMapper.selectStrategyCaseById(
                strategyCaseId
        );
        if (strategyCase == null) {
            throw failure(
                    "CALCULATION_CASE_NOT_FOUND",
                    "AI strategy case does not exist: " + strategyCaseId
            );
        }
        if (strategyCase.getCaseStatus() != StrategyCaseStatus.GENERATING
                || strategyCase.getGenerationStage()
                != StrategyGenerationStage.STRATEGY_GENERATING) {
            throw failure(
                    "CALCULATION_CASE_STAGE_INVALID",
                    "AI strategy case is not ready for strategy calculation"
            );
        }
        return strategyCase;
    }

    private StrategyCalculationContext assemble(
            StrategyCaseVO strategyCase,
            StrategyCaseRequestPayload payload,
            StrategyForecastResponse forecast,
            Long modelVersionId,
            List<Long> expectedSalesPointIds,
            String forecastRequestHash,
            LocalDateTime calculatedAt
    ) {
        LocalDate asOfDate = calculatedAt.toLocalDate();
        StrategyCalculationSkuVO sku = inputMapper.selectActiveSku(
                strategyCase.getSkuId()
        );
        if (sku == null) {
            throw failure(
                    "CALCULATION_SKU_NOT_FOUND",
                    "Active SKU does not exist: " + strategyCase.getSkuId()
            );
        }

        List<StrategyCalculationInventoryVO> inventory = inputMapper.selectInventory(
                strategyCase.getSkuId()
        );
        List<StrategyCalculationInventoryVO> evaluationInventory = selectEvaluationInventory(
                inventory,
                payload.lotIds(),
                strategyCase.getRequestedSalesPointId()
        );
        validateSingleWarehousePerLot(evaluationInventory);

        List<StrategyCalculationCostVO> costs = inputMapper.selectEffectiveCosts(
                strategyCase.getSkuId(),
                asOfDate
        );
        if (costs.size() != 1 || costs.get(0).getUnitCost() == null) {
            throw failure(
                    "CALCULATION_COST_INVALID",
                    "Exactly one effective SKU unit cost is required"
            );
        }

        List<StrategyCalculationSalesPointVO> salesPointRows =
                OracleInClauseBatcher.select(
                        expectedSalesPointIds,
                        inputMapper::selectActiveSalesPoints
                );
        validateSalesPointScope(expectedSalesPointIds, salesPointRows);
        List<StrategyCalculationPriceVO> priceRows = OracleInClauseBatcher.select(
                expectedSalesPointIds,
                salesPointIds -> inputMapper.selectEffectivePrices(
                        strategyCase.getSkuId(),
                        salesPointIds,
                        asOfDate
                )
        );
        Map<Long, List<StrategyCalculationWarehouseRouteVO>> routesBySalesPoint =
                OracleInClauseBatcher.select(
                        expectedSalesPointIds,
                        inputMapper::selectActiveWarehouseRoutes
                ).stream()
                        .collect(Collectors.groupingBy(
                                StrategyCalculationWarehouseRouteVO::getSalesPointId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        Map<Long, List<StrategyCalculationPriceVO>> pricesBySalesPoint = priceRows.stream()
                .collect(Collectors.groupingBy(
                        StrategyCalculationPriceVO::getSalesPointId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, SalesPointForecast> forecastsBySalesPoint = forecast.salesPointForecasts()
                .stream()
                .collect(Collectors.toMap(
                        SalesPointForecast::salesPointId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Long, StrategyCalculationContext.SalesPoint> salesPoints =
                new LinkedHashMap<>();
        for (StrategyCalculationSalesPointVO salesPoint : salesPointRows) {
            Long salesPointId = salesPoint.getSalesPointId();
            SalesPointForecast salesPointForecast = forecastsBySalesPoint.get(salesPointId);
            if (salesPointForecast == null) {
                throw failure(
                        "CALCULATION_FORECAST_SCOPE_INVALID",
                        "Demand forecast is missing for sales point: " + salesPointId
                );
            }
            StrategyCalculationContext.Price price = resolvePrice(
                    salesPointId,
                    pricesBySalesPoint.getOrDefault(salesPointId, List.of()),
                    Objects.equals(
                            salesPointId,
                            strategyCase.getRequestedSalesPointId()
                    )
            );
            Map<LocalDate, BigDecimal> dailyForecast = salesPointForecast
                    .futureDailyPredictions()
                    .stream()
                    .collect(Collectors.toMap(
                            prediction -> prediction.date(),
                            prediction -> prediction.predictedQty(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            salesPoints.put(salesPointId, new StrategyCalculationContext.SalesPoint(
                    salesPointId,
                    salesPoint.getSalesPointCode(),
                    salesPoint.getSalesPointName(),
                    existingAvailableQty(inventory, salesPointId, asOfDate),
                    !pricesBySalesPoint.getOrDefault(salesPointId, List.of()).isEmpty(),
                    price,
                    dailyForecast,
                    routesBySalesPoint.getOrDefault(salesPointId, List.of()).stream()
                            .map(this::toWarehouseRoute)
                            .toList()
            ));
        }

        List<StrategyCalculationPolicyVO> policyRows = inputMapper.selectEffectivePolicies(
                strategyCase.getSkuId(),
                asOfDate
        );
        List<StrategyCalculationTransferRouteVO> transferRoutes =
                relevantTransferRoutes(
                        inputMapper.selectActiveTransferRoutes(),
                        evaluationInventory,
                        salesPoints
                );
        List<StrategyCalculationTransferCostPolicyVO> transferCostPolicies =
                inputMapper.selectTransferCostPolicies(
                        forecast.forecastStartDate(),
                        forecast.forecastEndDate()
                );
        return new StrategyCalculationContext(
                strategyCase.getStrategyCaseId(),
                strategyCase.getRequestedSalesPointId(),
                calculatedAt,
                forecast.forecastStartDate(),
                forecast.forecastEndDate(),
                toSku(sku),
                costs.get(0).getUnitCost(),
                new StrategyCalculationContext.RequestConstraints(
                        payload.candidateSalesPointIds(),
                        payload.strategyTypes(),
                        payload.preferredStartDate(),
                        payload.preferredEndDate()
                ),
                evaluationInventory.stream().map(this::toInventoryLot).toList(),
                inventory.stream().map(this::toInventoryLot).toList(),
                policyRows.stream().map(this::toPolicy).toList(),
                salesPoints,
                new StrategyCalculationContext.ForecastMetadata(
                        forecast.forecastRunId(),
                        modelVersionId,
                        forecast.forecastGeneratedAt(),
                        forecastRequestHash
                ),
                transferRoutes.stream().map(this::toTransferRoute).toList(),
                transferCostPolicies.stream().map(this::toTransferCostPolicy).toList()
        );
    }

    private static List<StrategyCalculationInventoryVO> selectEvaluationInventory(
            List<StrategyCalculationInventoryVO> inventory,
            List<Long> requestedLotIds,
            Long sourceSalesPointId
    ) {
        Set<Long> requestedLots = new HashSet<>(requestedLotIds);
        List<StrategyCalculationInventoryVO> selected = inventory.stream()
                .filter(row -> row.getLotId() != null)
                .filter(row -> "AVAILABLE".equals(row.getLotStatus()))
                .filter(row -> requestedLots.isEmpty()
                        || requestedLots.contains(row.getLotId()))
                .filter(row -> matchesSource(row, sourceSalesPointId))
                .toList();

        if (!requestedLots.isEmpty()) {
            Set<Long> selectedLotIds = selected.stream()
                    .map(StrategyCalculationInventoryVO::getLotId)
                    .collect(Collectors.toSet());
            if (!selectedLotIds.equals(requestedLots)) {
                throw failure(
                        "CALCULATION_LOT_SCOPE_INVALID",
                        "Selected LOT is unavailable or does not belong to the source scope"
                );
            }
        }
        if (selected.isEmpty()) {
            throw failure(
                    "CALCULATION_INVENTORY_EMPTY",
                    "No available inventory exists in the evaluation scope"
            );
        }
        return List.copyOf(selected);
    }

    /** Case에서 실제로 출발·도착 가능성이 있는 방향성 경로만 Redis 문맥에 보존한다. */
    private static List<StrategyCalculationTransferRouteVO> relevantTransferRoutes(
            List<StrategyCalculationTransferRouteVO> routes,
            List<StrategyCalculationInventoryVO> evaluationInventory,
            Map<Long, StrategyCalculationContext.SalesPoint> salesPoints
    ) {
        Set<LocationKey> sourceLocations = evaluationInventory.stream()
                .map(row -> row.getWarehouseId() != null
                        ? new LocationKey(row.getWarehouseId(), null)
                        : new LocationKey(null, row.effectiveSalesPointId()))
                .filter(LocationKey::isValid)
                .collect(Collectors.toSet());
        Set<LocationKey> targetLocations = new HashSet<>();
        for (StrategyCalculationContext.SalesPoint salesPoint : salesPoints.values()) {
            targetLocations.add(new LocationKey(null, salesPoint.salesPointId()));
            salesPoint.warehouseRoutes().stream()
                    .map(route -> new LocationKey(route.warehouseId(), null))
                    .forEach(targetLocations::add);
        }
        return routes.stream()
                .filter(route -> sourceLocations.contains(new LocationKey(
                        route.getSourceWarehouseId(),
                        route.getSourceSalesPointId()
                )))
                .filter(route -> targetLocations.contains(new LocationKey(
                        route.getDestinationWarehouseId(),
                        route.getDestinationSalesPointId()
                )))
                .toList();
    }

    private static boolean matchesSource(
            StrategyCalculationInventoryVO inventory,
            Long sourceSalesPointId
    ) {
        if (sourceSalesPointId == null) {
            return inventory.isPublicUnassigned();
        }
        return Objects.equals(inventory.effectiveSalesPointId(), sourceSalesPointId);
    }

    private static void validateSingleWarehousePerLot(
            List<StrategyCalculationInventoryVO> inventory
    ) {
        Map<Long, Set<Long>> warehouseIdsByLot = new LinkedHashMap<>();
        for (StrategyCalculationInventoryVO row : inventory) {
            warehouseIdsByLot.computeIfAbsent(
                    row.getLotId(),
                    ignored -> new HashSet<>()
            ).add(row.getWarehouseId());
        }
        boolean splitWarehouseLotExists = warehouseIdsByLot.values().stream()
                .anyMatch(warehouseIds -> warehouseIds.size() > 1);
        if (splitWarehouseLotExists) {
            throw failure(
                    "CALCULATION_LOT_WAREHOUSE_INVALID",
                    "A LOT cannot be split across multiple warehouses"
            );
        }
    }

    private static void validateSalesPointScope(
            List<Long> expectedIds,
            List<StrategyCalculationSalesPointVO> actualRows
    ) {
        Set<Long> expected = new HashSet<>(expectedIds);
        Set<Long> actual = actualRows.stream()
                .map(StrategyCalculationSalesPointVO::getSalesPointId)
                .collect(Collectors.toSet());
        if (actualRows.size() != expected.size() || !actual.equals(expected)) {
            throw failure(
                    "CALCULATION_SALES_POINT_SCOPE_INVALID",
                    "Active sales point calculation scope has changed"
            );
        }
    }

    private static StrategyCalculationContext.Price resolvePrice(
            Long salesPointId,
            List<StrategyCalculationPriceVO> rows,
            boolean required
    ) {
        boolean complete = rows.size() == 1
                && rows.get(0).getSellingPrice() != null
                && rows.get(0).getActualPrice() != null
                && rows.get(0).getPaymentFee() != null
                && rows.get(0).getLogisticsCost() != null;
        if (!complete) {
            if (required) {
                throw failure(
                        "CALCULATION_SOURCE_PRICE_INVALID",
                        "Exactly one complete source price is required: " + salesPointId
                );
            }
            return null;
        }
        StrategyCalculationPriceVO row = rows.get(0);
        return new StrategyCalculationContext.Price(
                row.getSkuChannelPriceId(),
                row.getSellingPrice(),
                row.getActualPrice(),
                row.getMinimumSellingPrice(),
                row.getPaymentFee(),
                row.getLogisticsCost()
        );
    }

    /** 공용 미할당 재고는 어느 판매처에도 귀속하지 않고 재할당 후보의 원천으로만 사용한다. */
    private static BigDecimal existingAvailableQty(
            Collection<StrategyCalculationInventoryVO> inventory,
            Long salesPointId,
            LocalDate asOfDate
    ) {
        return inventory.stream()
                .filter(row -> Objects.equals(row.effectiveSalesPointId(), salesPointId))
                .filter(row -> isCurrentlySellable(row, asOfDate))
                .map(StrategyCalculationInventoryVO::getOnHandQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isCurrentlySellable(
            StrategyCalculationInventoryVO inventory,
            LocalDate asOfDate
    ) {
        return "AVAILABLE".equals(inventory.getLotStatus())
                && (inventory.getExpiryDate() == null
                || !asOfDate.isAfter(inventory.getExpiryDate()))
                && (inventory.getSaleStopDate() == null
                || asOfDate.isBefore(inventory.getSaleStopDate()));
    }

    private static StrategyCalculationContext.Sku toSku(
            StrategyCalculationSkuVO sku
    ) {
        return new StrategyCalculationContext.Sku(
                sku.getSkuId(),
                sku.getSkuCode(),
                sku.getSkuName(),
                sku.getUnitCode(),
                sku.getPackageQuantity(),
                sku.getNetWeight(),
                sku.getWeightUnit()
        );
    }

    private StrategyCalculationContext.InventoryLot toInventoryLot(
            StrategyCalculationInventoryVO inventory
    ) {
        return new StrategyCalculationContext.InventoryLot(
                inventory.getInventoryBalanceId(),
                inventory.getLotId(),
                inventory.getWarehouseId(),
                inventory.getStockSalesPointId(),
                inventory.getAllocatedSalesPointId(),
                inventory.getOnHandQty(),
                inventory.getReservedQty(),
                inventory.getManufacturedDate(),
                inventory.getReceivedDate(),
                inventory.getExpiryDate(),
                inventory.getSaleStopDate(),
                inventory.getLotStatus()
        );
    }

    private StrategyCalculationContext.InventoryPolicy toPolicy(
            StrategyCalculationPolicyVO policy
    ) {
        return new StrategyCalculationContext.InventoryPolicy(
                policy.getInventoryPolicyId(),
                policy.getWarehouseId(),
                policy.getStockSalesPointId(),
                policy.getAllocatedSalesPointId(),
                policy.getSafetyStockQty(),
                policy.getTargetStockQty(),
                policy.getDailyUnitHoldingCost(),
                policy.getUnitDisposalCost()
        );
    }

    private StrategyCalculationContext.WarehouseRoute toWarehouseRoute(
            StrategyCalculationWarehouseRouteVO route
    ) {
        return new StrategyCalculationContext.WarehouseRoute(
                route.getSalesPointWarehouseId(),
                route.getSalesPointId(),
                route.getWarehouseId(),
                route.getPriorityNo(),
                route.getBaseDeliveryCost()
        );
    }

    private StrategyCalculationContext.TransferRoute toTransferRoute(
            StrategyCalculationTransferRouteVO route
    ) {
        return new StrategyCalculationContext.TransferRoute(
                route.getTransferRouteId(),
                StrategyCalculationContext.PhysicalLocation.of(
                        route.getSourceWarehouseId(),
                        route.getSourceSalesPointId()
                ),
                StrategyCalculationContext.PhysicalLocation.of(
                        route.getDestinationWarehouseId(),
                        route.getDestinationSalesPointId()
                ),
                route.getDistanceKm(),
                route.getDistanceSource(),
                route.getDistanceRouteOption(),
                route.getDistanceCalculatedAt()
        );
    }

    private StrategyCalculationContext.TransferCostPolicy toTransferCostPolicy(
            StrategyCalculationTransferCostPolicyVO policy
    ) {
        return new StrategyCalculationContext.TransferCostPolicy(
                policy.getTransferCostPolicyId(),
                policy.getPolicyCode(),
                policy.getCostPerKgKm(),
                policy.getEffectiveFrom(),
                policy.getEffectiveTo()
        );
    }

    private static StrategyCalculationException failure(String code, String message) {
        return new StrategyCalculationException(code, message);
    }

    private record LocationKey(Long warehouseId, Long salesPointId) {
        private boolean isValid() {
            return (warehouseId == null) != (salesPointId == null);
        }
    }
}
