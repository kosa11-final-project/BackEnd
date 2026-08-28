package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 특정 판매처에 귀속되지 않은 물류센터 미할당 재고입니다.
 * 판매처별 재고와 구분해 재고 현황/상세 화면에서만 물류센터 위치를 표시합니다.
 */
@Schema(description = "물류센터 미할당 재고")
public record UnassignedInventoryResponse(
        @Schema(description = "미할당 현재고")
        BigDecimal currentQuantity,
        @Schema(description = "미할당 가용재고")
        BigDecimal availableQuantity,
        @Schema(description = "미할당 예약재고")
        BigDecimal reservedQuantity,
        @Schema(description = "미할당 재고 사실 상태")
        String inventoryFactState,
        @Schema(description = "미할당 재고 안전재고 미달 여부 (Y, N)", example = "Y")
        String shortageYn,
        @Schema(description = "미할당 재고 위험등급 (CRITICAL, WARNING, NORMAL, GOOD)", example = "WARNING")
        String riskGrade,
        @Schema(description = "미할당 재고 위험판정 상태", example = "ASSESSED")
        String assessmentStatus,
        @Schema(description = "미할당 재고 위험판정 사유")
        String riskReason,
        @Schema(description = "미할당 재고가 보관된 물류센터 목록")
        List<LocationResponse> locations,
        @Schema(description = "미할당 재고 보관 물류센터 수")
        int locationCount
) {

    public UnassignedInventoryResponse {
        locations = List.copyOf(locations == null ? List.of() : locations);
    }

    public static UnassignedInventoryResponse empty() {
        return new UnassignedInventoryResponse(null, null, null, null, null, null, null, null, List.of(), 0);
    }
}
