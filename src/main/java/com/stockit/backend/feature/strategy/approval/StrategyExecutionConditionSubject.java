package com.stockit.backend.feature.strategy.approval;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 변경된 재고·LOT·위치를 화면에서 식별하기 위한 선택적 참조값. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StrategyExecutionConditionSubject(
        Long inventoryBalanceId,
        Long lotId,
        Long warehouseId,
        Long salesPointId
) {
}
