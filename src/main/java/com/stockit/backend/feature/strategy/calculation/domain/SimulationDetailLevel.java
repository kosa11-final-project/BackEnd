package com.stockit.backend.feature.strategy.calculation.domain;

/** 후보군 비교 시에는 요약만, 최종 선택 후보에는 일별 시계열까지 생성한다. */
public enum SimulationDetailLevel {
    SUMMARY_ONLY,
    WITH_DAILY_SERIES
}
