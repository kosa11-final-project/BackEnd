package com.stockit.backend.feature.demandforecast.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.feature.demandforecast.service.DemandForecastService;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "수요 예측", description = "SKU당 판매처별 수요 예측 API")
@RestController
@RequestMapping("/api/v1/demand-forecasts")
public class DemandForecastController {

    private final DemandForecastService demandForecastService;

    public DemandForecastController(DemandForecastService demandForecastService) {
        this.demandForecastService = demandForecastService;
    }
}
