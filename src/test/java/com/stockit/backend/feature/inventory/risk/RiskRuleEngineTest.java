package com.stockit.backend.feature.inventory.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RiskRuleEngine 위험 평가 규칙 엔진 테스트")
class RiskRuleEngineTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 16);
    private RiskRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RiskRuleEngine(Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), SEOUL));
    }

    @Test
    @DisplayName("가용수량이 없으면 재고 위험으로 판정한다")
    void missingInventory_isCritical() {
        RiskAssessmentResult result = engine.evaluate(input(null, BigDecimal.TEN,
                BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.apiRiskGrade()).isEqualTo("DANGER");
        assertThat(result.availableQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.reasons()).extracting(RiskReason::code).contains("DATA_MISSING");
    }

    @Test
    @DisplayName("inventory_balance.on_hand_qty를 가용수량으로 사용한다")
    void onHandQty_isAvailableQty() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(20), List.of(), true, false));

        assertThat(result.availableQty()).isEqualByComparingTo("100");
        assertThat(result.shortageQty30()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.apiRiskGrade()).isEqualTo("SAFE");
    }

    @Test
    @DisplayName("D+30 재고일수가 14일 미만이면 주의 등급을 반환한다")
    void severeForecastShortage_isCaution() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(300), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("WARNING");
        assertThat(result.apiRiskGrade()).isEqualTo("CAUTION");
        assertThat(result.shortageQty30()).isEqualByComparingTo("200");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("PREDICTED_SHORTAGE");
    }

    @Test
    @DisplayName("D+7은 안전하고 D+30 재고일수가 14일 이상이면 보통 등급으로 관리한다")
    void mildForecastShortage_isNormal() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(48),
                BigDecimal.valueOf(15), BigDecimal.valueOf(59), BigDecimal.valueOf(10), List.of(), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.apiRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.ruleVersion()).isEqualTo("v1.3.0");
        assertThat(result.shortageQty30()).isEqualByComparingTo("11");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("PREDICTED_SHORTAGE_MONITORING")
                .doesNotContain("PREDICTED_SHORTAGE");
    }

    @Test
    @DisplayName("D+7 예상잔고가 안전재고보다 낮으면 위험 등급을 반환한다")
    void projectedInventoryBelowSafety_isDanger() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(90), BigDecimal.valueOf(100), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.apiRiskGrade()).isEqualTo("DANGER");
        assertThat(result.projectedD7()).isEqualByComparingTo("10");
        assertThat(result.safetyGapQty()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("소비기한 만료 LOT가 있으면 위험 등급을 반환한다")
    void expiredLot_isDanger() {
        RiskAssessmentInput.LotRiskItem lot = lot(BASE_DATE.minusDays(1), null, BASE_DATE.minusDays(30));
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(lot), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.apiRiskGrade()).isEqualTo("DANGER");
        assertThat(result.availableQty()).isEqualByComparingTo("100");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("LOT_EXPIRED");
    }

    @Test
    @DisplayName("만료 LOT가 있어도 현재 가용수량은 canonical on_hand_qty를 유지한다")
    void availableQty_keepsCanonicalOnHandUntilSeparateColumnExists() {
        RiskAssessmentInput.LotRiskItem expired = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-EXPIRED", BASE_DATE.minusDays(1), null, BASE_DATE.minusDays(30), BigDecimal.valueOf(40), "EXPIRED");
        RiskAssessmentInput.LotRiskItem valid = new RiskAssessmentInput.LotRiskItem(
                "2", "LOT-VALID", BASE_DATE.plusDays(30), null, BASE_DATE.minusDays(3), BigDecimal.valueOf(60), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(expired, valid), true, false));

        assertThat(result.availableQty()).isEqualByComparingTo("100");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("LOT_EXPIRED");
    }

    @Test
    @DisplayName("판매중지 LOT는 미할당 scope에서도 가용수량과 위험판정에 반영한다")
    void unassignedScope_marksSaleStoppedLotWithoutChangingAvailableQty() {
        RiskAssessmentInput.LotRiskItem stopped = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-STOPPED", BASE_DATE.plusDays(30), BASE_DATE.minusDays(1), BASE_DATE.minusDays(3), BigDecimal.valueOf(25), "SALE_STOPPED");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(25),
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(5), BASE_DATE,
                List.of(stopped), true, false));

        assertThat(result.availableQty()).isEqualByComparingTo("25");
        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("LOT_SALE_STOPPED");
    }

    @Test
    @DisplayName("오래된 예측 기준일도 예측값을 사용해 판정한다")
    void staleForecast_isStillAssessed() {
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30),
                BASE_DATE.minusDays(15), List.of(), true, true));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("GOOD");
        assertThat(result.apiRiskGrade()).isEqualTo("SAFE");
        assertThat(result.projectedD7()).isEqualByComparingTo("80");
        assertThat(result.primaryReason()).contains("기준일이 오래된 수요예측");
    }

    @Test
    @DisplayName("오래된 예측을 사용해도 소비기한과 보유일수는 현재 판정일 기준으로 계산한다")
    void staleForecast_usesCurrentAssessmentDateForLotRules() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-OLD-FORECAST", BASE_DATE.minusDays(1), null, BASE_DATE.minusDays(10), BigDecimal.TEN
        );
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30),
                BASE_DATE.minusDays(15), List.of(lot), true, true, BASE_DATE
        ));

        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("LOT_EXPIRED");
        assertThat(result.nearestExpiryDays()).isEqualTo(-1);
        assertThat(result.maxHoldingDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("예측이 없고 서버 룰상 위험이 없으면 보통으로 판정한다")
    void missingForecast_usesAvailableRules() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                null, null, BigDecimal.valueOf(30), List.of(), false, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.apiRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.shortageQty30()).isNull();
        assertThat(result.projectedD7()).isNull();
        assertThat(result.reasons()).extracting(RiskReason::code).contains("FORECAST_UNAVAILABLE");
        assertThat(result.primaryReason()).contains("수요예측이 없어");
    }

    @Test
    @DisplayName("예측이 없어도 현재 재고가 안전재고 미만이면 서버 룰로 주의 판정한다")
    void missingForecast_currentUnderSafety_isCaution() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(20),
                null, null, BigDecimal.valueOf(30), List.of(), false, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("WARNING");
        assertThat(result.apiRiskGrade()).isEqualTo("CAUTION");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("FORECAST_UNAVAILABLE", "CURRENT_UNDER_SAFETY");
    }

    @Test
    @DisplayName("누적 예측값이 잘못되면 예측 규칙만 제외하고 판정한다")
    void decreasingForecast_skipsInvalidForecastRules() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(80), BigDecimal.valueOf(70), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.apiRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.shortageQty30()).isNull();
        assertThat(result.reasons()).extracting(RiskReason::code).contains("FORECAST_INVALID");
        assertThat(result.primaryReason()).contains("유효하지 않아");
    }

    @Test
    @DisplayName("수요예측·재고·정책·LOT만으로 판정한다")
    void usesOnlyForecastInventoryPolicyAndLot() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.apiRiskGrade()).isEqualTo("SAFE");
    }

    @Test
    @DisplayName("음수 안전재고 기준은 입력 오류로 차단한다")
    void negativeSafetyStock_isRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> engine.evaluate(input(
                BigDecimal.valueOf(100), BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(-1), List.of(), true, false)));
    }

    @Test
    @DisplayName("음수 LOT 수량은 입력 오류로 차단한다")
    void negativeLotQuantity_isRejected() {
        RiskAssessmentInput.LotRiskItem invalidLot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-01", BASE_DATE.plusDays(30), null, BASE_DATE.minusDays(5), BigDecimal.valueOf(-1)
        );
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> engine.evaluate(input(
                BigDecimal.valueOf(100), BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(invalidLot), true, false)));
    }

    private RiskAssessmentInput input(
            BigDecimal onHandQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD30,
            BigDecimal safetyStockQty,
            List<RiskAssessmentInput.LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale
    ) {
        return new RiskAssessmentInput(
                "SKU-001", "GREETING", onHandQty,
                predictedQtyD7, predictedQtyD30, safetyStockQty,
                BASE_DATE, lots, forecastAvailable, forecastStale
        );
    }

    private RiskAssessmentInput.LotRiskItem lot(LocalDate expiryDate, LocalDate saleStopDate, LocalDate receivedDate) {
        return new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-01", expiryDate, saleStopDate, receivedDate, BigDecimal.TEN
        );
    }
}
