package com.stockit.backend.feature.statistics.service;

import java.time.LocalDate;

import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsResponse;

public interface StrategyStatisticsService {

    StrategyStatisticsResponse getStrategyStatistics(
            LocalDate fromDate,
            LocalDate toDate,
            StatisticsScopeType scopeType,
            String scopeCode
    );
}
