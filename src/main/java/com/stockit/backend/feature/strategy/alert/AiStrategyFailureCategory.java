package com.stockit.backend.feature.strategy.alert;

/** 최종 실패 코드를 IT 담당자가 바로 분류할 수 있는 운영 장애 영역으로 변환한다. */
public enum AiStrategyFailureCategory {
    MESSAGE_QUEUE,
    DEMAND_FORECAST,
    MASTER_DATA,
    STRATEGY_CALCULATION,
    GEMINI,
    REDIS,
    DATABASE_STATE,
    UNKNOWN;

    public static AiStrategyFailureCategory from(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return UNKNOWN;
        }
        if (failureCode.startsWith("MQ_")) {
            return MESSAGE_QUEUE;
        }
        if (failureCode.startsWith("FORECAST_")) {
            return DEMAND_FORECAST;
        }
        if (isMasterDataFailure(failureCode)) {
            return MASTER_DATA;
        }
        if (failureCode.startsWith("LLM_")) {
            return GEMINI;
        }
        if (failureCode.contains("CACHE")
                || failureCode.contains("SIMULATION_CONTEXT")) {
            return REDIS;
        }
        if (failureCode.contains("STAGE_TRANSITION")
                || failureCode.startsWith("STRATEGY_CASE_")) {
            return DATABASE_STATE;
        }
        if (failureCode.startsWith("CALCULATION_")
                || failureCode.startsWith("CANDIDATE_")
                || failureCode.startsWith("FINAL_SIMULATION_")
                || failureCode.startsWith("STRATEGY_CANDIDATE_")) {
            return STRATEGY_CALCULATION;
        }
        return UNKNOWN;
    }

    private static boolean isMasterDataFailure(String failureCode) {
        return "STRATEGY_INPUT_DATA_UNAVAILABLE".equals(failureCode)
                || "CALCULATION_COST_INVALID".equals(failureCode)
                || "CALCULATION_SOURCE_PRICE_INVALID".equals(failureCode)
                || "CANDIDATE_PRICE_NOT_FOUND".equals(failureCode)
                || "CANDIDATE_INVENTORY_COST_POLICY_NOT_FOUND".equals(failureCode);
    }
}
