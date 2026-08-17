package com.stockit.backend.feature.demandforecast.service;

import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastResponse;

public interface DemandForecastService {

    DemandForecastImportResponse importForecasts(DemandForecastImportRequest request, Long userId);

    DemandForecastResponse getForecast(String skuCode, String salesPointCode);

    DemandForecastResponse getSkuAggregateForecast(String skuCode);
}
