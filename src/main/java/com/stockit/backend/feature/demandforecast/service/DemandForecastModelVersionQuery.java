package com.stockit.backend.feature.demandforecast.service;

/** 외부 수요예측 모델 식별자에 대응하는 내부 모델 버전 ID 조회 계약. */
public interface DemandForecastModelVersionQuery {

    /**
     * 모델명과 논리 버전에 대응하는 내부 모델 버전 ID를 조회한다.
     *
     * @param modelName 외부 ML 모델명
     * @param modelVersion 외부 ML 논리 버전
     * @return 내부 모델 버전 ID, 등록되지 않았으면 {@code null}
     */
    Long findModelVersionId(String modelName, String modelVersion);
}
