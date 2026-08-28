package com.stockit.backend.feature.inventory.dto.response;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesPointResponseTest {

    @Test
    void legacyConstructorDoesNotAdvertiseMissingPriceAsAvailable() {
        SalesPointResponse withoutPrice = new SalesPointResponse(
                "GREETING",
                "그리팅",
                "GREETING",
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                "GOOD",
                "스마트푸드센터"
        );

        assertThat(withoutPrice.priceStatus()).isEqualTo("NOT_LOADED");
    }

    @Test
    void priceConstructorMarksAnActualPriceAvailable() {
        SalesPointResponse withPrice = new SalesPointResponse(
                "GREETING",
                "그리팅",
                "GREETING",
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                "GOOD",
                "스마트푸드센터",
                BigDecimal.valueOf(12000)
        );

        assertThat(withPrice.priceStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void carriesThePerSalesPointSafetyStockShortageFlag() {
        SalesPointResponse point = new SalesPointResponse(
                77L,
                "GREETING",
                "그리팅",
                "GREETING",
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                "GOOD",
                "Y",
                null,
                null,
                "OWNED",
                "NOT_LOADED"
        );

        assertThat(point.shortageYn()).isEqualTo("Y");
    }
}
