package com.stockit.backend.feature.demandforecast.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.demandforecast.service.DemandForecastModelVersionQuery;

/** 수요예측 모델 자연키를 내부 모델 버전 ID로 조회한다. */
@Service
@Transactional(readOnly = true)
public class DemandForecastModelVersionQueryService
        implements DemandForecastModelVersionQuery {

    private final DemandForecastMapper demandForecastMapper;

    public DemandForecastModelVersionQueryService(
            DemandForecastMapper demandForecastMapper
    ) {
        this.demandForecastMapper = demandForecastMapper;
    }

    @Override
    public Long findModelVersionId(String modelName, String modelVersion) {
        return demandForecastMapper.selectModelVersionId(modelName, modelVersion);
    }
}
