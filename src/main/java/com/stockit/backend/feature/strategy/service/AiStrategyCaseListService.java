package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseListPageResponse;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListQuery;

public interface AiStrategyCaseListService {

    AiStrategyCaseListPageResponse findAll(AiStrategyCaseListQuery query);
}
