package com.stockit.backend.feature.demandforecast.service;

import com.stockit.backend.feature.demandforecast.dto.request.DemandForecastImportRequest;
import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastImportResponse;

public interface DemandForecastService {

    DemandForecastImportResponse importForecasts(DemandForecastImportRequest request, Long userId);
}
