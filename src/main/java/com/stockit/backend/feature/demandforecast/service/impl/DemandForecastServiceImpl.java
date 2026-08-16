package com.stockit.backend.feature.demandforecast.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportItemRequest;
import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.demandforecast.service.DemandForecastService;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;

@Service
public class DemandForecastServiceImpl implements DemandForecastService {

    private final DemandForecastMapper demandForecastMapper;

    public DemandForecastServiceImpl(DemandForecastMapper demandForecastMapper) {
        this.demandForecastMapper = demandForecastMapper;
    }

    @Override
    @Transactional
    public DemandForecastImportResponse importForecasts(
            DemandForecastImportRequest request,
            Long userId
    ) {
        Long modelVersionId = demandForecastMapper.selectModelVersionId(
                request.modelName(),
                request.modelVersion()
        );
        if (modelVersionId == null) {
            throw new AppException(ErrorCode.DEMAND_FORECAST_MODEL_NOT_FOUND);
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

        List<DemandForecastVO> forecasts = request.forecasts().stream()
                .map(item -> DemandForecastVO.forImport(
                        item.skuId(),
                        item.salesPointId(),
                        modelVersionId,
                        request.forecastBaseDate(),
                        item.predictedQtyD7(),
                        item.predictedQtyD14(),
                        item.predictedQtyD30(),
                        item.predictedQtyD60(),
                        item.predictedQtyD90(),
                        item.forecastSource(),
                        item.confidenceLevel(),
                        userId
                ))
                .toList();

        demandForecastMapper.mergeDemandForecasts(forecasts);
        return DemandForecastImportResponse.from(request, modelVersionId, forecasts.size());
    }

    private record ForecastTarget(Long skuId, Long salesPointId) {
    }
}
