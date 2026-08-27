package com.stockit.backend.feature.demandforecast.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportItemRequest;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;
import com.stockit.backend.feature.demandforecast.dto.response.CumulativeForecastDto;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastResponse;
import com.stockit.backend.feature.demandforecast.dto.response.FreshnessDto;
import com.stockit.backend.feature.demandforecast.dto.response.ProjectedInventoryDto;
import com.stockit.backend.feature.demandforecast.domain.DemandForecastRunNotificationEvent;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.demandforecast.service.DemandForecastPayloadHash;
import com.stockit.backend.feature.demandforecast.service.DemandForecastService;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastStagingVO;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;

@Service
@Transactional(readOnly = true)
public class DemandForecastServiceImpl implements DemandForecastService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private final DemandForecastMapper demandForecastMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public DemandForecastServiceImpl(
            DemandForecastMapper demandForecastMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this(demandForecastMapper, Clock.system(BUSINESS_ZONE), eventPublisher);
    }

    public DemandForecastServiceImpl(DemandForecastMapper demandForecastMapper) {
        this(demandForecastMapper, Clock.system(BUSINESS_ZONE), event -> { });
    }

    DemandForecastServiceImpl(DemandForecastMapper demandForecastMapper, Clock clock) {
        this(demandForecastMapper, clock, event -> { });
    }

    DemandForecastServiceImpl(
            DemandForecastMapper demandForecastMapper,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.demandForecastMapper = demandForecastMapper;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public DemandForecastResponse getForecast(String skuCode, String salesPointCode) {
        String normalizedSkuCode = requiredCode(skuCode, "skuCode");
        String normalizedSalesPointCode = requiredCode(salesPointCode, "salesPointCode");

        LocalDate today = LocalDate.now(clock);
        DemandForecastVO forecastVO = demandForecastMapper.selectDemandForecast(normalizedSkuCode, normalizedSalesPointCode);
        LocalDate observationDate = forecastVO != null && forecastVO.getBaseDate() != null
                ? forecastVO.getBaseDate()
                : today;
        BigDecimal safetyStockQty = demandForecastMapper.selectSafetyStockQty(
                normalizedSkuCode,
                normalizedSalesPointCode,
                observationDate
        );
        BigDecimal availableQty = demandForecastMapper.selectAvailableQty(normalizedSkuCode, normalizedSalesPointCode);

        return buildResponse(
                "SALES_POINT",
                normalizedSkuCode,
                normalizedSalesPointCode,
                today,
                forecastVO,
                safetyStockQty,
                availableQty
        );
    }

    @Override
    public DemandForecastResponse getSkuAggregateForecast(String skuCode) {
        String normalizedSkuCode = requiredCode(skuCode, "skuCode");

        LocalDate today = LocalDate.now(clock);
        DemandForecastVO forecastVO = demandForecastMapper.selectSkuAggregateDemandForecast(normalizedSkuCode);
        LocalDate observationDate = forecastVO != null && forecastVO.getBaseDate() != null
                ? forecastVO.getBaseDate()
                : today;
        BigDecimal safetyStockQty = demandForecastMapper.selectSkuAggregateSafetyStockQty(
                normalizedSkuCode,
                observationDate
        );
        BigDecimal availableQty = demandForecastMapper.selectSkuAggregateAvailableQty(normalizedSkuCode);

        return buildResponse(
                "SKU_AGGREGATE",
                normalizedSkuCode,
                "ALL",
                today,
                forecastVO,
                safetyStockQty,
                availableQty
        );
    }

    private DemandForecastResponse buildResponse(
            String scope,
            String skuCode,
            String salesPointCode,
            LocalDate today,
            DemandForecastVO forecastVO,
            BigDecimal safetyStockQty,
            BigDecimal availableQty
    ) {
        // 누적 예측과 예상 잔고만 조합합니다. 실제 판매 시계열은 ML 파이프라인의
        // 입력이며 이 조회 화면의 응답·차트에는 포함하지 않습니다.
        BigDecimal d7 = forecastVO != null ? forecastVO.getPredictedQtyD7() : null;
        BigDecimal d14 = forecastVO != null ? forecastVO.getPredictedQtyD14() : null;
        BigDecimal d30 = forecastVO != null ? forecastVO.getPredictedQtyD30() : null;
        BigDecimal d60 = forecastVO != null ? forecastVO.getPredictedQtyD60() : null;
        BigDecimal d90 = forecastVO != null ? forecastVO.getPredictedQtyD90() : null;
        boolean invalidForecast = forecastVO != null && hasInvalidForecastHorizon(d7, d14, d30, d60, d90);
        if (invalidForecast) {
            d7 = null;
            d14 = null;
            d30 = null;
            d60 = null;
            d90 = null;
        }

        CumulativeForecastDto cumulativeForecast = new CumulativeForecastDto(d7, d14, d30, d60, d90);

        BigDecimal currentAvailable = availableQty;
        ProjectedInventoryDto projectedInventories = calculateProjectedInventory(currentAvailable, d7, d14, d30, d60, d90);

        // 상태는 예측 데이터 자체의 품질과 기준일만 판단합니다.
        LocalDate baseDate = forecastVO != null ? forecastVO.getBaseDate() : null;
        boolean isStale = baseDate != null && ChronoUnit.DAYS.between(baseDate, today) > 14;
        boolean hasForecastData = forecastVO != null && hasAllForecastHorizons(d7, d14, d30, d60, d90);
        boolean invalidSafetyStock = safetyStockQty != null && safetyStockQty.signum() < 0;

        // 안전재고 정책은 기준선·위험판정에 필요한 선택 데이터입니다.
        // 유효한 forecast가 있으면 정책이 없어도 예측·예상 잔고는 먼저 제공합니다.
        String status = "AVAILABLE";
        String qualityMsg = safetyStockQty == null
                ? "수요예측은 정상 조회되었지만 안전재고 기준이 아직 적재되지 않아 기준선은 표시되지 않습니다."
                : "수요예측과 안전재고 기준이 정상적으로 조회되었습니다.";

        if (invalidForecast) {
            status = "ERROR";
            qualityMsg = "누적 수요예측의 필수 구간이 누락되었거나 값이 음수·감소하여 사용할 수 없습니다.";
        } else if (!hasForecastData) {
            status = "NO_DATA";
            qualityMsg = "해당 상품 및 판매처의 수요예측 데이터가 없습니다.";
        } else if (invalidSafetyStock) {
            status = "ERROR";
            qualityMsg = "안전재고 기준이 음수여서 수요예측 기준선을 표시할 수 없습니다.";
        } else if (isStale) {
            status = "STALE";
            qualityMsg = "수요예측 데이터가 기준일로부터 오래되었습니다 (Stale).";
        }

        Instant lastUpdatedAt = forecastVO != null && forecastVO.getUpdatedAt() != null
                ? forecastVO.getUpdatedAt().atZone(BUSINESS_ZONE).toInstant()
                : null;
        FreshnessDto freshness = new FreshnessDto(lastUpdatedAt, isStale, status, qualityMsg, baseDate);

        String confidenceLevel = forecastVO != null ? forecastVO.getConfidenceLevel() : null;
        BigDecimal confidence = confidenceScore(confidenceLevel);

        return new DemandForecastResponse(
                status,
                scope,
                skuCode,
                forecastVO != null && forecastVO.getSkuName() != null ? forecastVO.getSkuName() : skuCode,
                salesPointCode,
                forecastVO != null && forecastVO.getSalesPointName() != null
                        ? forecastVO.getSalesPointName()
                        : salesPointCode,
                baseDate,
                forecastVO != null ? forecastVO.getModelVersion() : null,
                forecastVO != null ? forecastVO.getForecastSource() : null,
                confidence,
                confidenceLevel,
                currentAvailable,
                safetyStockQty,
                cumulativeForecast,
                projectedInventories,
                freshness
        );
    }

    private BigDecimal confidenceScore(String confidenceLevel) {
        if (confidenceLevel == null || confidenceLevel.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(confidenceLevel);
        } catch (NumberFormatException ignored) {
            return switch (confidenceLevel.toUpperCase()) {
                case "HIGH" -> BigDecimal.valueOf(0.95);
                case "MEDIUM" -> BigDecimal.valueOf(0.75);
                case "LOW" -> BigDecimal.valueOf(0.50);
                default -> null;
            };
        }
    }

    private static String requiredCode(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new AppException(ErrorCode.INVALID_PARAMETER, field + "는 1~100자여야 합니다.");
        }
        return value.trim();
    }

    private boolean hasAllForecastHorizons(
            BigDecimal d7,
            BigDecimal d14,
            BigDecimal d30,
            BigDecimal d60,
            BigDecimal d90
    ) {
        return d7 != null && d14 != null && d30 != null && d60 != null && d90 != null;
    }

    private boolean hasInvalidForecastHorizon(
            BigDecimal d7,
            BigDecimal d14,
            BigDecimal d30,
            BigDecimal d60,
            BigDecimal d90
    ) {
        if (!hasAllForecastHorizons(d7, d14, d30, d60, d90)) {
            return true;
        }
        BigDecimal previous = null;
        for (BigDecimal current : new BigDecimal[]{d7, d14, d30, d60, d90}) {
            if (current == null) {
                continue;
            }
            if (current.signum() < 0 || (previous != null && current.compareTo(previous) < 0)) {
                return true;
            }
            previous = current;
        }
        return false;
    }

    private ProjectedInventoryDto calculateProjectedInventory(
            BigDecimal available,
            BigDecimal d7, BigDecimal d14, BigDecimal d30, BigDecimal d60, BigDecimal d90
    ) {
        if (available == null) {
            return new ProjectedInventoryDto(null, null, null, null, null, "재고 미적재");
        }

        BigDecimal proj7 = d7 != null ? available.subtract(d7).max(BigDecimal.ZERO) : null;
        BigDecimal proj14 = d14 != null ? available.subtract(d14).max(BigDecimal.ZERO) : null;
        BigDecimal proj30 = d30 != null ? available.subtract(d30).max(BigDecimal.ZERO) : null;
        BigDecimal proj60 = d60 != null ? available.subtract(d60).max(BigDecimal.ZERO) : null;
        BigDecimal proj90 = d90 != null ? available.subtract(d90).max(BigDecimal.ZERO) : null;

        String stockout = "90일 이상 재고 유지";
        if (proj7 != null && proj7.compareTo(BigDecimal.ZERO) == 0) {
            stockout = "D+1~D+7";
        } else if (proj14 != null && proj14.compareTo(BigDecimal.ZERO) == 0) {
            stockout = "D+8~D+14";
        } else if (proj30 != null && proj30.compareTo(BigDecimal.ZERO) == 0) {
            stockout = "D+15~D+30";
        } else if (proj60 != null && proj60.compareTo(BigDecimal.ZERO) == 0) {
            stockout = "D+31~D+60";
        } else if (proj90 != null && proj90.compareTo(BigDecimal.ZERO) == 0) {
            stockout = "D+61~D+90";
        }

        return new ProjectedInventoryDto(proj7, proj14, proj30, proj60, proj90, stockout);
    }

    @Override
    @Transactional
    public DemandForecastImportResponse importForecasts(
            DemandForecastImportRequest request,
            Long userId
    ) {
        DemandForecastRunVO run = demandForecastMapper.selectRunByAzureJobIdForUpdate(
                request.azureJobId()
        );
        if (run == null) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_RUN_NOT_FOUND);
        }

        String payloadHash = DemandForecastPayloadHash.calculate(request);
        String existingPayloadHash = demandForecastMapper.selectBatchPayloadHash(
                run.getForecastRunId(),
                request.batchNumber()
        );
        if (existingPayloadHash != null) {
            if (!existingPayloadHash.equals(payloadHash)) {
                throw new AppException(ErrorCode.DEMAND_FORECAST_BATCH_CONFLICT);
            }
            return DemandForecastImportResponse.from(
                    request,
                    run.getModelVersionId(),
                    0,
                    run.getReceivedBatches(),
                    run.getReceivedItems(),
                    true,
                    run.getRunStatus()
            );
        }

        if (!"RUNNING".equals(run.getRunStatus())) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_RUN_CONFLICT);
        }

        Long modelVersionId = demandForecastMapper.selectModelVersionId(
                request.modelName(),
                request.modelVersion()
        );
        if (modelVersionId == null) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_MODEL_NOT_FOUND);
        }

        int initialized = demandForecastMapper.initializeImportManifest(
                run.getForecastRunId(),
                modelVersionId,
                request.forecastBaseDate(),
                request.totalBatches(),
                request.totalItems(),
                userId
        );
        if (initialized != 1) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_RUN_CONFLICT);
        }

        Set<ForecastTarget> targets = new HashSet<>();
        Set<Long> skuIds = new HashSet<>();
        Set<Long> salesPointIds = new HashSet<>();
        for (DemandForecastImportItemRequest forecast : request.forecasts()) {
            if (!targets.add(new ForecastTarget(forecast.skuId(), forecast.salesPointId()))) {
                throw new AppException(ErrorCode.DEMAND_FORECAST_DUPLICATE_TARGET);
            }
            skuIds.add(forecast.skuId());
            salesPointIds.add(forecast.salesPointId());
        }

        List<Long> distinctSkuIds = List.copyOf(skuIds);
        if (demandForecastMapper.countExistingSkus(distinctSkuIds) != distinctSkuIds.size()) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_SKU_NOT_FOUND);
        }

        List<Long> distinctSalesPointIds = List.copyOf(salesPointIds);
        if (demandForecastMapper.countExistingSalesPoints(distinctSalesPointIds)
                != distinctSalesPointIds.size()) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_SALES_POINT_NOT_FOUND);
        }

        List<DemandForecastStagingVO> forecasts = request.forecasts().stream()
                .map(item -> stagingForecast(run.getForecastRunId(), request.batchNumber(), item, userId))
                .toList();

        try {
            demandForecastMapper.insertStagingForecasts(forecasts);
        } catch (DuplicateKeyException exception) {
            throw new AppException(
                    ErrorCode.DEMAND_FORECAST_DUPLICATE_TARGET,
                    "서로 다른 배치에 동일한 SKU와 판매처 조합이 포함되어 있습니다."
            );
        }
        demandForecastMapper.insertImportBatch(
                run.getForecastRunId(),
                request.batchNumber(),
                forecasts.size(),
                payloadHash,
                userId
        );

        int receivedBatches = demandForecastMapper.countReceivedBatches(run.getForecastRunId());
        long receivedItems = demandForecastMapper.sumReceivedItems(run.getForecastRunId());
        Long effectiveTotalItems = request.totalItems();
        if (effectiveTotalItems == null && receivedBatches == request.totalBatches()) {
            effectiveTotalItems = receivedItems;
        }
        if (receivedBatches > request.totalBatches()
                || (effectiveTotalItems != null && receivedItems > effectiveTotalItems)) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_RUN_CONFLICT);
        }
        if (demandForecastMapper.updateImportProgress(
                run.getForecastRunId(), receivedBatches, receivedItems, effectiveTotalItems, userId
        ) != 1) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_RUN_CONFLICT);
        }

        String runStatus = "RUNNING";
        if (receivedBatches == request.totalBatches()) {
            int stagingCount = demandForecastMapper.countStagingForecasts(run.getForecastRunId());
            if (effectiveTotalItems == null
                    || receivedItems != effectiveTotalItems
                    || stagingCount != effectiveTotalItems) {
                throw new AppException(
                        ErrorCode.DEMAND_FORECAST_RUN_CONFLICT,
                        "전체 배치 수신 건수와 전체 예측 데이터 건수가 일치하지 않습니다."
                );
            }
            demandForecastMapper.softDeleteObsoleteForecasts(run.getForecastRunId(), userId);
            demandForecastMapper.mergeStagingForecasts(run.getForecastRunId(), userId);
            if (demandForecastMapper.markRunSucceeded(run.getForecastRunId(), userId) != 1) {
                throw new AppException(ErrorCode.DEMAND_FORECAST_RUN_CONFLICT);
            }
            runStatus = "SUCCEEDED";
            eventPublisher.publishEvent(new DemandForecastRunNotificationEvent(
                    run.getForecastRunId(),
                    "DEMAND_FORECAST_COMPLETED",
                    "INFO",
                    "일일 수요예측 완료",
                    request.forecastBaseDate() + " 기준 수요예측 " + receivedItems
                            + "건이 정상 반영되었습니다.",
                    "DEMAND_FORECAST:" + run.getForecastRunId() + ":COMPLETED",
                    request.forecastBaseDate(),
                    "FINALIZING",
                    null,
                    request.azureJobId(),
                    Instant.now()
            ));
        }

        return DemandForecastImportResponse.from(
                request,
                modelVersionId,
                forecasts.size(),
                receivedBatches,
                receivedItems,
                false,
                runStatus
        );
    }

    private static DemandForecastStagingVO stagingForecast(
            Long runId,
            Integer batchNumber,
            DemandForecastImportItemRequest item,
            Long userId
    ) {
        DemandForecastStagingVO forecast = new DemandForecastStagingVO();
        forecast.setForecastRunId(runId);
        forecast.setBatchNumber(batchNumber);
        forecast.setSkuId(item.skuId());
        forecast.setSalesPointId(item.salesPointId());
        forecast.setPredictedQtyD7(item.predictedQtyD7());
        forecast.setPredictedQtyD14(item.predictedQtyD14());
        forecast.setPredictedQtyD30(item.predictedQtyD30());
        forecast.setPredictedQtyD60(item.predictedQtyD60());
        forecast.setPredictedQtyD90(item.predictedQtyD90());
        forecast.setForecastSource(item.forecastSource());
        forecast.setConfidenceLevel(item.confidenceLevel());
        forecast.setCreatedBy(userId);
        return forecast;
    }

    private record ForecastTarget(Long skuId, Long salesPointId) {
    }
}
