package com.stockit.backend.feature.strategy.forecast;

/**
 * 전략 생성에 사용할 일별 판매량 예측을 외부 모델로부터 조회하는 Port
 */
public interface ForecastProvider {

    /**
     * 확정된 Case 요청 범위에 대한 판매처별 일별 수요예측 실행
     */
    StrategyForecastResponse forecast(StrategyForecastRequest request);
}
