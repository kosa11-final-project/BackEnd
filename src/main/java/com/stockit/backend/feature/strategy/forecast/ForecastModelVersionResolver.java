package com.stockit.backend.feature.strategy.forecast;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.demandforecast.service.DemandForecastModelVersionQuery;
import com.stockit.backend.feature.strategy.domain.StrategyGenerationStage;
import com.stockit.backend.feature.strategy.messaging.PermanentStrategyGenerationException;
import com.stockit.backend.feature.strategy.messaging.RetryableStrategyGenerationException;

/** 외부 ML 모델 식별자를 내부 {@code ml_model_version} PK로 변환한다. */
@Component
public class ForecastModelVersionResolver {

    private final DemandForecastModelVersionQuery modelVersionQuery;

    public ForecastModelVersionResolver(
            DemandForecastModelVersionQuery modelVersionQuery
    ) {
        this.modelVersionQuery = modelVersionQuery;
    }

    public Long resolve(StrategyForecastResponse response) {
        try {
            Long modelVersionId = modelVersionQuery.findModelVersionId(
                    response.modelName(),
                    response.modelVersion()
            );
            if (modelVersionId == null) {
                throw new PermanentStrategyGenerationException(
                        "FORECAST_MODEL_VERSION_NOT_REGISTERED",
                        StrategyGenerationStage.FORECASTING,
                        "Demand forecast model is not registered: "
                                + response.modelName() + ":" + response.modelVersion()
                );
            }
            return modelVersionId;
        } catch (PermanentStrategyGenerationException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new RetryableStrategyGenerationException(
                    "FORECAST_MODEL_VERSION_LOOKUP_FAILED",
                    StrategyGenerationStage.FORECASTING,
                    "Failed to resolve demand forecast model version",
                    exception
            );
        }
    }
}
