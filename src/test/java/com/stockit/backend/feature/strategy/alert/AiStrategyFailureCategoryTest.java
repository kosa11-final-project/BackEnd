package com.stockit.backend.feature.strategy.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiStrategyFailureCategoryTest {

    @ParameterizedTest
    @CsvSource(value = {
            "MQ_PUBLISH_FAILED, MESSAGE_QUEUE",
            "FORECAST_API_TIMEOUT, DEMAND_FORECAST",
            "CALCULATION_COST_INVALID, MASTER_DATA",
            "CALCULATION_FORECAST_INVALID, STRATEGY_CALCULATION",
            "LLM_API_UNAVAILABLE, GEMINI",
            "FORECAST_CACHE_UNAVAILABLE, DEMAND_FORECAST",
            "STRATEGY_RESULT_CACHE_UNAVAILABLE, REDIS",
            "STRATEGY_CASE_STAGE_INVALID, DATABASE_STATE",
            "UNEXPECTED_FAILURE, UNKNOWN",
            "NULL, UNKNOWN"
    }, nullValues = "NULL")
    void classifiesFinalFailureCode(String failureCode, String expected) {
        assertThat(AiStrategyFailureCategory.from(failureCode).name())
                .isEqualTo(expected);
    }
}
