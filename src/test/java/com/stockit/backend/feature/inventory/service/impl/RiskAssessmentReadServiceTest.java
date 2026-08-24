package com.stockit.backend.feature.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.inventory.dto.response.RiskAssessmentDetailResponse;
import com.stockit.backend.feature.inventory.mapper.RiskAssessmentMapper;
import com.stockit.backend.feature.inventory.risk.InventoryQuantityVO;
import com.stockit.backend.feature.inventory.risk.PersistedRiskAssessmentVO;
import com.stockit.backend.feature.inventory.risk.RiskForecastVO;
import com.stockit.backend.feature.inventory.risk.RiskRuleEngine;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentReadServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate FORECAST_BASE_DATE = LocalDate.of(2026, 7, 31);

    @Mock
    private RiskAssessmentMapper mapper;

    private RiskAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), SEOUL);
        service = new RiskAssessmentServiceImpl(mapper, new RiskRuleEngine(), clock);
    }

    @Test
    void evaluatesForecastAgainstItsBaseDateButCurrentRulesAgainstToday() {
        RiskForecastVO forecast = new RiskForecastVO();
        forecast.setBaseDate(FORECAST_BASE_DATE);
        forecast.setPredictedQtyD7(new BigDecimal("20"));
        forecast.setPredictedQtyD30(new BigDecimal("80"));
        when(mapper.selectLatestForecast("SKU-1", "GREETING")).thenReturn(forecast);

        InventoryQuantityVO quantities = new InventoryQuantityVO();
        quantities.setOnHandQty(new BigDecimal("100"));
        when(mapper.selectInventoryQuantities("SKU-1", "GREETING")).thenReturn(quantities);

        when(mapper.selectSafetyStock("SKU-1", "GREETING", LocalDate.of(2026, 8, 10)))
                .thenReturn(new BigDecimal("10"));
        when(mapper.selectLotRiskItems("SKU-1", "GREETING", LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of());

        RiskAssessmentDetailResponse response = service.getRiskAssessment("SKU-1", "GREETING");

        assertThat(response.baseDate()).isEqualTo(FORECAST_BASE_DATE);
        assertThat(response.availableQty()).isEqualByComparingTo("100");
        assertThat(response.stockCoverageDays()).isEqualByComparingTo("37.5");
        assertThat(response.shortageYn()).isEqualTo("N");
        verify(mapper).selectSafetyStock("SKU-1", "GREETING", LocalDate.of(2026, 8, 10));
        verify(mapper).selectLotRiskItems("SKU-1", "GREETING", LocalDate.of(2026, 8, 10));
    }

    @Test
    void usesTheRiskAssessmentValuesPersistedByTheLastInventorySync() {
        RiskForecastVO forecast = new RiskForecastVO();
        forecast.setBaseDate(FORECAST_BASE_DATE);
        forecast.setPredictedQtyD7(new BigDecimal("20"));
        forecast.setPredictedQtyD30(new BigDecimal("80"));
        when(mapper.selectLatestForecast("SKU-1", "GREETING")).thenReturn(forecast);

        InventoryQuantityVO quantities = new InventoryQuantityVO();
        quantities.setOnHandQty(new BigDecimal("100"));
        when(mapper.selectInventoryQuantities("SKU-1", "GREETING")).thenReturn(quantities);
        when(mapper.selectSafetyStock("SKU-1", "GREETING", LocalDate.of(2026, 8, 10)))
                .thenReturn(new BigDecimal("10"));
        when(mapper.selectLotRiskItems("SKU-1", "GREETING", LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of());

        PersistedRiskAssessmentVO persisted = new PersistedRiskAssessmentVO();
        persisted.setDbRiskGrade("WARNING");
        persisted.setShortageYn("Y");
        persisted.setStockDays(new BigDecimal("42"));
        persisted.setHoldingDays(11);
        persisted.setExpiryDaysLeft(17);
        persisted.setRuleVersion("v1.1.0");
        persisted.setReasonMessage("[ASSESSED/v1.1.0/PREDICTED_SHORTAGE] 동기화 당시 저장한 판정 사유");
        persisted.setAssessedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        when(mapper.selectLatestPersistedAssessment("SKU-1", "GREETING")).thenReturn(persisted);

        RiskAssessmentDetailResponse response = service.getRiskAssessment("SKU-1", "GREETING");

        assertThat(response.dbRiskGrade()).isEqualTo("WARNING");
        assertThat(response.reasonMessage()).isEqualTo(persisted.getReasonMessage());
        assertThat(response.ruleVersion()).isEqualTo("v1.1.0");
        assertThat(response.assessedAt()).isEqualTo(persisted.getAssessedAt().toInstant());
        assertThat(response.stockCoverageDays()).isEqualByComparingTo("42");
        assertThat(response.maxHoldingDays()).isEqualTo(11);
        assertThat(response.nearestExpiryDays()).isEqualTo(17);
        assertThat(response.shortageYn()).isEqualTo("Y");
    }
}
