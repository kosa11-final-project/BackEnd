package com.stockit.backend.feature.demandforecast.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.demandforecast.dto.response.DemandForecastResponse;
import com.stockit.backend.feature.demandforecast.mapper.DemandForecastMapper;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;

@ExtendWith(MockitoExtension.class)
class DemandForecastReadServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 10);
    private static final LocalDate FORECAST_BASE_DATE = LocalDate.of(2026, 7, 31);

    @Mock
    private DemandForecastMapper mapper;

    private DemandForecastServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-10T00:00:00Z"),
                SEOUL
        );
        service = new DemandForecastServiceImpl(mapper, clock);
    }

    @Test
    void usesForecastBaseDateForSafetyPolicyOnly() {
        DemandForecastVO forecast = forecast(FORECAST_BASE_DATE);

        when(mapper.selectDemandForecast("SKU-1", "GREETING")).thenReturn(forecast);
        when(mapper.selectAvailableQty("SKU-1", "GREETING")).thenReturn(new BigDecimal("100"));
        when(mapper.selectSafetyStockQty("SKU-1", "GREETING", FORECAST_BASE_DATE))
                .thenReturn(new BigDecimal("20"));

        DemandForecastResponse response = service.getForecast("SKU-1", "GREETING");

        assertThat(response.baseDate()).isEqualTo(FORECAST_BASE_DATE);
        assertThat(response.freshness().forecastAsOf()).isEqualTo(FORECAST_BASE_DATE);
        assertThat(response.availableQty()).isEqualByComparingTo("100");
    }

    @Test
    void noForecastDoesNotInventBaseDateModelOrSource() {
        when(mapper.selectDemandForecast("SKU-1", "GREETING")).thenReturn(null);
        when(mapper.selectAvailableQty("SKU-1", "GREETING")).thenReturn(BigDecimal.ZERO);

        DemandForecastResponse response = service.getForecast("SKU-1", "GREETING");

        assertThat(response.status()).isEqualTo("NO_DATA");
        assertThat(response.baseDate()).isNull();
        assertThat(response.modelVersion()).isNull();
        assertThat(response.forecastSource()).isNull();
        assertThat(response.freshness().forecastAsOf()).isNull();
        assertThat(response.freshness().lastUpdatedAt()).isNull();
    }

    @Test
    void missingRequiredHorizonIsDataErrorInsteadOfPartialForecast() {
        DemandForecastVO forecast = forecast(FORECAST_BASE_DATE);
        forecast.setPredictedQtyD60(null);
        when(mapper.selectDemandForecast("SKU-1", "GREETING")).thenReturn(forecast);
        when(mapper.selectAvailableQty("SKU-1", "GREETING")).thenReturn(BigDecimal.TEN);

        DemandForecastResponse response = service.getForecast("SKU-1", "GREETING");

        assertThat(response.status()).isEqualTo("ERROR");
        assertThat(response.cumulativeForecast().predictedQtyD7()).isNull();
        assertThat(response.freshness().message()).contains("필수 구간");
    }

    @Test
    void missingSafetyPolicyDoesNotClaimForecastIsAvailable() {
        DemandForecastVO forecast = forecast(FORECAST_BASE_DATE);
        when(mapper.selectDemandForecast("SKU-1", "GREETING")).thenReturn(forecast);
        when(mapper.selectAvailableQty("SKU-1", "GREETING")).thenReturn(BigDecimal.TEN);
        when(mapper.selectSafetyStockQty("SKU-1", "GREETING", FORECAST_BASE_DATE)).thenReturn(null);

        DemandForecastResponse response = service.getForecast("SKU-1", "GREETING");

        assertThat(response.status()).isEqualTo("ERROR");
        assertThat(response.freshness().message()).contains("안전재고 기준");
    }

    private static DemandForecastVO forecast(LocalDate baseDate) {
        DemandForecastVO forecast = new DemandForecastVO();
        forecast.setForecastId(1L);
        forecast.setSkuId(1L);
        forecast.setSalesPointId(1L);
        forecast.setModelVersionId(7L);
        forecast.setBaseDate(baseDate);
        forecast.setPredictedQtyD7(new BigDecimal("7"));
        forecast.setPredictedQtyD14(new BigDecimal("14"));
        forecast.setPredictedQtyD30(new BigDecimal("30"));
        forecast.setPredictedQtyD60(new BigDecimal("60"));
        forecast.setPredictedQtyD90(new BigDecimal("90"));
        forecast.setForecastSource("LIGHTGBM");
        forecast.setConfidenceLevel("HIGH");
        return forecast;
    }
}
