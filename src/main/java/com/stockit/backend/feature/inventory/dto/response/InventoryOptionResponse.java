package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "통합 재고 필터 옵션")
public record InventoryOptionResponse(
        @Schema(description = "업무 코드 또는 categoryId 문자열", example = "GYEONGIN_1")
        String code,
        @Schema(description = "표시 이름", example = "경인 1센터")
        String name,
        @Schema(description = "상위 옵션 코드 또는 parent category id")
        String parentCode,
        @Schema(description = "지역 코드")
        String regionCode,
        @Schema(description = "정규화된 판매 채널")
        String channelType,
        @Schema(description = "센터 availability: ACTIVE 또는 REGISTERED_EMPTY")
        String availability,
        @Schema(description = "현재 재고가 있는 SKU 수")
        Long currentSkuCount,
        @Schema(description = "현재 재고 balance 행 수")
        Long currentBalanceRowCount,
        @Schema(description = "현재고 합계")
        BigDecimal currentOnHandQty,
        @Schema(description = "카테고리 level")
        Integer level
) {
}
