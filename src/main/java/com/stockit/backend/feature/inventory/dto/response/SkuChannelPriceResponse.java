package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "판매처별 SKU 판매가 정보")
public record SkuChannelPriceResponse(
        @Schema(description = "판매처 코드", example = "GREETING")
        String salesPointCode,

        @Schema(description = "판매처명", example = "그리팅몰")
        String salesPointName,

        @Schema(description = "현재 판매가", example = "12000")
        BigDecimal sellingPrice,

        @Schema(description = "정가 / 실판매 기준가", example = "15000")
        BigDecimal actualPrice,

        @Schema(description = "최저 판매가", example = "10000")
        BigDecimal minimumSellingPrice,

        @Schema(description = "가격 적용 시작일", example = "2026-01-01")
        LocalDate effectiveFrom,

        @Schema(description = "가격 적용 종료일", example = "2099-12-31")
        LocalDate effectiveTo,

        @Schema(description = "가격 상태 (AVAILABLE, NOT_LOADED, STALE)", example = "AVAILABLE")
        String priceStatus
) {
}
