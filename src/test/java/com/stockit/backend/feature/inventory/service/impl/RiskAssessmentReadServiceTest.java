package com.stockit.backend.feature.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import com.stockit.backend.feature.inventory.risk.RiskAssessmentInput;
import com.stockit.backend.feature.inventory.risk.RiskRuleEngine;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentReadServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate FORECAST_BASE_DATE = LocalDate.of(2026, 8, 9);

    @Mock
    private RiskAssessmentMapper mapper;

    private RiskAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), SEOUL);
        service = new RiskAssessmentServiceImpl(mapper, new RiskRuleEngine(clock), clock);
    }

    @Test
    void evaluatesForecastAgainstItsBaseDateButReportsTheAssessmentDate() {
        RiskForecastVO forecast = new RiskForecastVO();
        forecast.setBaseDate(FORECAST_BASE_DATE);
        forecast.setPredictedQtyD7(new BigDecimal("20"));
        forecast.setPredictedQtyD14(new BigDecimal("40"));
        forecast.setPredictedQtyD30(new BigDecimal("80"));
        forecast.setPredictedQtyD60(new BigDecimal("140"));
        forecast.setPredictedQtyD90(new BigDecimal("200"));
        when(mapper.selectLatestForecast("SKU-1", "GREETING", LocalDate.of(2026, 8, 10))).thenReturn(forecast);

        InventoryQuantityVO quantities = new InventoryQuantityVO();
        quantities.setOnHandQty(new BigDecimal("100"));
        when(mapper.selectInventoryQuantities("SKU-1", "GREETING")).thenReturn(quantities);

        when(mapper.selectSafetyStock("SKU-1", "GREETING", LocalDate.of(2026, 8, 10)))
                .thenReturn(new BigDecimal("10"));
        when(mapper.selectLotRiskItems("SKU-1", "GREETING"))
                .thenReturn(List.of());

        RiskAssessmentDetailResponse response = service.getRiskAssessment("SKU-1", "GREETING");

        assertThat(response.baseDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(response.availableQty()).isEqualByComparingTo("100");
        assertThat(response.stockCoverageDays()).isEqualByComparingTo("37.5");
        assertThat(response.shortageYn()).isEqualTo("N");
        verify(mapper).selectSafetyStock("SKU-1", "GREETING", LocalDate.of(2026, 8, 10));
        verify(mapper).selectLotRiskItems("SKU-1", "GREETING");
    }

    @Test
    void exposesTheServerCalculatedExpectedDisposalQuantityRateAndSaleEndDays() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        RiskForecastVO forecast = new RiskForecastVO();
        forecast.setBaseDate(today);
        forecast.setPredictedQtyD7(new BigDecimal("10"));
        forecast.setPredictedQtyD14(new BigDecimal("20"));
        forecast.setPredictedQtyD30(new BigDecimal("25"));
        forecast.setPredictedQtyD60(new BigDecimal("40"));
        forecast.setPredictedQtyD90(new BigDecimal("55"));
        when(mapper.selectLatestForecast("SKU-1", "GREETING", today)).thenReturn(forecast);

        InventoryQuantityVO quantities = new InventoryQuantityVO();
        quantities.setOnHandQty(new BigDecimal("100"));
        when(mapper.selectInventoryQuantities("SKU-1", "GREETING")).thenReturn(quantities);
        when(mapper.selectSafetyStock("SKU-1", "GREETING", today)).thenReturn(new BigDecimal("5"));
        when(mapper.selectLotRiskItems("SKU-1", "GREETING")).thenReturn(List.of(
                new RiskAssessmentInput.LotRiskItem(
                        "1", "LOT-1", today.plusDays(20), null, today.minusDays(3),
                        new BigDecimal("50"), "AVAILABLE"
                )
        ));

        RiskAssessmentDetailResponse response = service.getRiskAssessment("SKU-1", "GREETING");

        assertThat(response.expectedDisposalQty30()).isEqualByComparingTo("28.125");
        assertThat(response.expectedDisposalRate30()).isEqualByComparingTo("28.13");
        assertThat(response.nearestSaleEndDays()).isEqualTo(20);
        assertThat(response.assessmentStatus()).isEqualTo("UNASSESSED");
        assertThat(response.riskGrade()).isNull();
        assertThat(response.dbRiskGrade()).isNull();
        assertThat(response.reasonMessage()).isNull();
        assertThat(response.ruleVersion()).isNull();
        assertThat(response.assessedAt()).isNull();
        assertThat(response.reasons()).isEmpty();
    }

    @Test
    void usesTheLastSynchronizedAssessmentAsTheCanonicalRiskStatus() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        RiskForecastVO forecast = new RiskForecastVO();
        forecast.setBaseDate(today);
        forecast.setPredictedQtyD7(new BigDecimal("10"));
        forecast.setPredictedQtyD14(new BigDecimal("20"));
        forecast.setPredictedQtyD30(new BigDecimal("25"));
        forecast.setPredictedQtyD60(new BigDecimal("40"));
        forecast.setPredictedQtyD90(new BigDecimal("55"));
        when(mapper.selectLatestForecast("SKU-1", "GREETING", today)).thenReturn(forecast);

        InventoryQuantityVO quantities = new InventoryQuantityVO();
        quantities.setOnHandQty(new BigDecimal("100"));
        when(mapper.selectInventoryQuantities("SKU-1", "GREETING")).thenReturn(quantities);
        when(mapper.selectSafetyStock("SKU-1", "GREETING", today)).thenReturn(new BigDecimal("10"));
        when(mapper.selectLotRiskItems("SKU-1", "GREETING")).thenReturn(List.of(
                new RiskAssessmentInput.LotRiskItem(
                        "1", "LOT-1", today.plusDays(20), null, today.minusDays(3),
                        new BigDecimal("50"), "AVAILABLE"
                )
        ));

        PersistedRiskAssessmentVO persisted = new PersistedRiskAssessmentVO();
        persisted.setDbRiskGrade("WARNING");
        persisted.setShortageYn("Y");
        persisted.setStockDays(new BigDecimal("17"));
        persisted.setHoldingDays(11);
        persisted.setExpiryDaysLeft(5);
        persisted.setRuleVersion("v1.6.0");
        persisted.setReasonMessage("[ASSESSED/v1.6.0/EXPECTED_DISPOSAL_CAUTION] 동기화 당시 저장한 주의 판정");
        persisted.setAssessedAt(Timestamp.from(Instant.parse("2026-08-08T15:30:00Z")));
        when(mapper.selectLatestPersistedAssessment("SKU-1", "GREETING")).thenReturn(persisted);

        RiskAssessmentDetailResponse response = service.getRiskAssessment("SKU-1", "GREETING");

        assertThat(response.riskGrade()).isEqualTo("WARNING");
        assertThat(response.dbRiskGrade()).isEqualTo("WARNING");
        assertThat(response.reasonMessage()).isEqualTo(persisted.getReasonMessage());
        assertThat(response.ruleVersion()).isEqualTo("v1.6.0");
        assertThat(response.assessedAt()).isEqualTo(persisted.getAssessedAt().toInstant());
        assertThat(response.baseDate()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(response.stockCoverageDays()).isEqualByComparingTo("120.0");
        assertThat(response.shortageYn()).isEqualTo("N");
        assertThat(response.maxHoldingDays()).isEqualTo(3);
        assertThat(response.nearestExpiryDays()).isEqualTo(20);
        assertThat(response.expectedDisposalQty30()).isEqualByComparingTo("28.125");
        assertThat(response.expectedDisposalRate30()).isEqualByComparingTo("28.13");
        assertThat(response.nearestSaleEndDays()).isEqualTo(20);
        assertThat(response.reasons()).singleElement().satisfies(reason -> {
            assertThat(reason.code()).isEqualTo("CANONICAL_REASON");
            assertThat(reason.message()).isEqualTo(persisted.getReasonMessage());
        });
        verify(mapper).selectLatestPersistedAssessment("SKU-1", "GREETING");
    }

    @Test
    void exposesOnlyCurrentInformationalGuidanceWithoutRepeatingTheCanonicalReason() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        RiskForecastVO forecast = new RiskForecastVO();
        forecast.setBaseDate(today);
        forecast.setPredictedQtyD7(new BigDecimal("20"));
        forecast.setPredictedQtyD14(new BigDecimal("50"));
        forecast.setPredictedQtyD30(new BigDecimal("100"));
        forecast.setPredictedQtyD60(new BigDecimal("150"));
        forecast.setPredictedQtyD90(new BigDecimal("200"));
        when(mapper.selectLatestForecast("SKU-1", "GREETING", today)).thenReturn(forecast);

        InventoryQuantityVO quantities = new InventoryQuantityVO();
        quantities.setOnHandQty(new BigDecimal("100"));
        when(mapper.selectInventoryQuantities("SKU-1", "GREETING")).thenReturn(quantities);
        when(mapper.selectSafetyStock("SKU-1", "GREETING", today)).thenReturn(new BigDecimal("10"));
        when(mapper.selectLotRiskItems("SKU-1", "GREETING")).thenReturn(List.of(
                new RiskAssessmentInput.LotRiskItem(
                        "1", "LOT-1", today.plusDays(20), null, today.minusDays(3),
                        new BigDecimal("50"), "AVAILABLE"
                )
        ));

        PersistedRiskAssessmentVO persisted = new PersistedRiskAssessmentVO();
        persisted.setDbRiskGrade("GOOD");
        persisted.setShortageYn("N");
        persisted.setStockDays(new BigDecimal("30"));
        persisted.setRuleVersion("v1.6.0");
        persisted.setReasonMessage("현재 가용재고와 LOT 상태가 양호해 안정적인 재고 상태입니다.");
        persisted.setAssessedAt(Timestamp.from(Instant.parse("2026-08-08T15:30:00Z")));
        when(mapper.selectLatestPersistedAssessment("SKU-1", "GREETING")).thenReturn(persisted);

        RiskAssessmentDetailResponse response = service.getRiskAssessment("SKU-1", "GREETING");

        assertThat(response.riskGrade()).isEqualTo("GOOD");
        assertThat(response.reasonMessage()).isEqualTo(persisted.getReasonMessage());
        assertThat(response.reasons()).singleElement().satisfies(reason -> {
            assertThat(reason.code()).isEqualTo("CANONICAL_REASON");
            assertThat(reason.message()).isEqualTo(persisted.getReasonMessage());
        });
    }

    @Test
    void keepsNullablePersistedMetricsNullUntilTheNextSynchronization() {
        LocalDate today = LocalDate.of(2026, 8, 10);
        RiskForecastVO forecast = new RiskForecastVO();
        forecast.setBaseDate(today);
        forecast.setPredictedQtyD7(new BigDecimal("10"));
        forecast.setPredictedQtyD14(new BigDecimal("20"));
        forecast.setPredictedQtyD30(new BigDecimal("25"));
        forecast.setPredictedQtyD60(new BigDecimal("40"));
        forecast.setPredictedQtyD90(new BigDecimal("55"));
        when(mapper.selectLatestForecast("SKU-1", "GREETING", today)).thenReturn(forecast);

        InventoryQuantityVO quantities = new InventoryQuantityVO();
        quantities.setOnHandQty(new BigDecimal("100"));
        when(mapper.selectInventoryQuantities("SKU-1", "GREETING")).thenReturn(quantities);
        when(mapper.selectSafetyStock("SKU-1", "GREETING", today)).thenReturn(new BigDecimal("10"));
        when(mapper.selectLotRiskItems("SKU-1", "GREETING")).thenReturn(List.of(
                new RiskAssessmentInput.LotRiskItem(
                        "1", "LOT-1", today.plusDays(20), null, today.minusDays(3),
                        new BigDecimal("50"), "AVAILABLE"
                )
        ));

        PersistedRiskAssessmentVO persisted = new PersistedRiskAssessmentVO();
        persisted.setDbRiskGrade("NORMAL");
        persisted.setShortageYn("N");
        persisted.setRuleVersion("v1.6.0");
        persisted.setReasonMessage("[ASSESSED/v1.6.0/RULE_EVALUATION] 동기화 당시 저장한 보통 판정");
        persisted.setAssessedAt(Timestamp.from(Instant.parse("2026-08-08T15:30:00Z")));
        when(mapper.selectLatestPersistedAssessment("SKU-1", "GREETING")).thenReturn(persisted);

        RiskAssessmentDetailResponse response = service.getRiskAssessment("SKU-1", "GREETING");

        assertThat(response.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(response.riskGrade()).isEqualTo("NORMAL");
        assertThat(response.stockCoverageDays()).isEqualByComparingTo("120.0");
        assertThat(response.nearestExpiryDays()).isEqualTo(20);
        assertThat(response.maxHoldingDays()).isEqualTo(3);
        assertThat(response.expectedDisposalQty30()).isEqualByComparingTo("28.125");
        assertThat(response.expectedDisposalRate30()).isEqualByComparingTo("28.13");
    }
}
