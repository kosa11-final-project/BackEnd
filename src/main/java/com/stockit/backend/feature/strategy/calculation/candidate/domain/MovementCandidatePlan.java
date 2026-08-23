package com.stockit.backend.feature.strategy.calculation.candidate.domain;

import java.math.BigDecimal;
import java.util.List;

/** LOT 소비기한과 판매처의 미충족 수요를 함께 반영한 수량 배분 결과. */
public record MovementCandidatePlan(
        BigDecimal plannedQuantity,
        List<Allocation> allocations
) {
    public MovementCandidatePlan {
        allocations = List.copyOf(allocations);
    }

    public record Allocation(
            Long inventoryBalanceId,
            Long lotId,
            Long sourceWarehouseId,
            Long sourceSalesPointId,
            BigDecimal quantity
    ) {
    }
}
