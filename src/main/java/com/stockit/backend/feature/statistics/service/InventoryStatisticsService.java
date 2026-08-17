package com.stockit.backend.feature.statistics.service;

import java.time.LocalDate;

import com.stockit.backend.feature.statistics.domain.StatisticsScopeType;
import com.stockit.backend.feature.statistics.dto.response.InventoryStatisticsResponse;

public interface InventoryStatisticsService {

    InventoryStatisticsResponse getInventoryStatistics(
            LocalDate fromDate,
            LocalDate toDate,
            StatisticsScopeType trendScopeType,
            String trendScopeCode
    );
}
