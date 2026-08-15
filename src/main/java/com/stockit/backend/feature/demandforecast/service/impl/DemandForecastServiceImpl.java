package com.stockit.backend.feature.demandforecast.service.impl;

import org.springframework.stereotype.Service;

import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.demandforecast.service.DemandForecastService;

@Service
public class DemandForecastServiceImpl implements DemandForecastService {

    private final DemandForecastMapper demandForecastMapper;

    public DemandForecastServiceImpl(DemandForecastMapper demandForecastMapper) {
        this.demandForecastMapper = demandForecastMapper;
    }
}
