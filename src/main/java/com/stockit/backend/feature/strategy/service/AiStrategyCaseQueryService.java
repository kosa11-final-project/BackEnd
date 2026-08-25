package com.stockit.backend.feature.strategy.service;

import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;

public interface AiStrategyCaseQueryService {
    AiStrategyCaseResponse find(Long strategyCaseId);
}
