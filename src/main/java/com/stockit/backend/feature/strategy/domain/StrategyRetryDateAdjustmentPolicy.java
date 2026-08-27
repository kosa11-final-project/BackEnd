package com.stockit.backend.feature.strategy.domain;

/** 실패 Case 재시도 시 과거 사용자 지정 시작일을 처리하는 정책. */
public enum StrategyRetryDateAdjustmentPolicy {
    /** 보정이 필요하면 신규 Case를 만들지 않고 사용자 확인 정보를 반환한다. */
    REJECT,
    /** 과거 사용자 지정 시작일을 서버의 현재 업무일로 변경한다. */
    ADJUST_TO_TODAY
}
