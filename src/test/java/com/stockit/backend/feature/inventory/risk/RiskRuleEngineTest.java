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
        assertThat(result.apiRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.availableQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.reasons()).extracting(RiskReason::code).contains("DATA_MISSING");
        assertThat(result.primaryReason()).isEqualTo("현재 재고수량을 확인할 수 없어 판매 가능 재고와 부족 위험을 판정할 수 없습니다.");
    }

    @Test
    @DisplayName("inventory_balance.on_hand_qty를 가용수량으로 사용한다")
    void onHandQty_isAvailableQty() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(20), List.of(), true, false));

        assertThat(result.availableQty()).isEqualByComparingTo("100");
        assertThat(result.shortageQty30()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("D+30 재고일수가 14일 미만이면 위험 등급을 반환한다")
    void severeForecastShortage_isCaution() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(300), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.apiRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.shortageQty30()).isEqualByComparingTo("200");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("STOCKOUT_WITHIN_14_DAYS");
        assertThat(result.primaryReason()).contains("약 10일 후 재고가 소진될 것으로 예상됩니다");
    }

    @Test
    @DisplayName("D+7은 안전하고 D+30 재고일수가 14일 초과 30일 미만이면 주의로 관리한다")
    void mildForecastShortage_isNormal() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(48),
                BigDecimal.valueOf(15), BigDecimal.valueOf(59), BigDecimal.valueOf(10), List.of(), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("WARNING");
        assertThat(result.apiRiskGrade()).isEqualTo("WARNING");
        assertThat(result.ruleVersion()).isEqualTo("v1.8.0");
        assertThat(result.shortageQty30()).isEqualByComparingTo("11");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("STOCKOUT_WITHIN_30_DAYS")
                .doesNotContain("PREDICTED_SHORTAGE");
        assertThat(result.primaryReason()).contains("약 24.41일 후 재고가 소진될 것으로 예상됩니다");
    }

    @Test
    @DisplayName("D+7 예상잔고가 안전재고보다 낮으면 위험 등급을 반환한다")
    void projectedInventoryBelowSafety_isDanger() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(90), BigDecimal.valueOf(100), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.apiRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.projectedD7()).isEqualByComparingTo("10");
        assertThat(result.safetyGapQty()).isEqualByComparingTo("20");
        assertThat(result.primaryReason()).contains("7일 후 예상 재고 10개가 안전재고 30개보다 20개 부족할 것으로 예상됩니다");
    }

    @Test
    @DisplayName("소비기한 만료 LOT만 남으면 판매 가능 재고 0으로 위험 판정한다")
    void expiredLotOnly_isZeroSellableStock() {
        RiskAssessmentInput.LotRiskItem lot = lot(BASE_DATE.minusDays(1), null, BASE_DATE.minusDays(30));
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.TEN,
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(lot), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.apiRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.availableQty()).isEqualByComparingTo("0");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("ZERO_AVAILABLE_STOCK", "LOT_EXPIRED_EXCLUDED")
                .doesNotContain("LOT_EXPIRED");
    }

    @Test
    @DisplayName("만료 LOT를 제외하고 남은 LOT로 재고와 가장 가까운 소비기한을 판정한다")
    void expiredLot_isExcludedAndRemainingLotDeterminesRisk() {
        RiskAssessmentInput.LotRiskItem expired = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-EXPIRED", BASE_DATE.minusDays(1), null, BASE_DATE.minusDays(30), BigDecimal.valueOf(40), "EXPIRED");
        RiskAssessmentInput.LotRiskItem valid = new RiskAssessmentInput.LotRiskItem(
                "2", "LOT-VALID", BASE_DATE.plusDays(60), null, BASE_DATE.minusDays(3), BigDecimal.valueOf(60), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.TEN, BigDecimal.valueOf(40), BigDecimal.valueOf(30), List.of(expired, valid), true, false));

        assertThat(result.availableQty()).isEqualByComparingTo("60");
        assertThat(result.nearestExpiryDays()).isEqualTo(60);
        assertThat(result.dbRiskGrade()).isEqualTo("GOOD");
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("LOT_EXPIRED_EXCLUDED")
                .doesNotContain("EXPIRY_WARNING", "LOT_EXPIRED");
    }

    @Test
    @DisplayName("원천 LOT 상태는 무시하고 기준일과 날짜 규칙으로 상태를 다시 판정한다")
    void sourceLotStatus_isIgnoredDuringResolution() {
        RiskAssessmentInput.LotRiskItem sourceMarkedExpired = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-SOURCE-AVAILABLE", BASE_DATE.plusDays(10), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(20), "EXPIRED");
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(20),
                null, null, null, BigDecimal.TEN, BASE_DATE,
                List.of(sourceMarkedExpired), false, false, BASE_DATE));

        assertThat(result.availableQty()).isEqualByComparingTo("20");
        assertThat(result.nearestExpiryDays()).isEqualTo(10);
        assertThat(result.reasons()).extracting(RiskReason::code)
                .doesNotContain("LOT_EXPIRED_EXCLUDED");
    }

    @Test
    @DisplayName("소비기한과 판매중지일이 같은 날이면 소비기한 만료를 우선한다")
    void equalExpiryAndSaleStopDate_prefersExpired() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-SAME-DATE", BASE_DATE, BASE_DATE,
                BASE_DATE.minusDays(3), BigDecimal.TEN, "SALE_STOPPED");
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.TEN,
                null, null, null, BigDecimal.ONE, BASE_DATE,
                List.of(lot), false, false, BASE_DATE));

        assertThat(result.availableQty()).isEqualByComparingTo("0");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("LOT_EXPIRED_EXCLUDED")
                .doesNotContain("LOT_SALE_STOPPED_EXCLUDED");
    }

    @Test
    @DisplayName("D+60·D+90 장기 과잉은 주의로 반영하되 위험까지 올리지 않는다")
    void longTermOverstock_isCautionButNotDanger() {
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(100),
                BigDecimal.TEN, BigDecimal.valueOf(20), BigDecimal.valueOf(30),
                BigDecimal.valueOf(40), BigDecimal.valueOf(50), BigDecimal.TEN, BASE_DATE,
                List.of(), true, false, BASE_DATE));

        assertThat(result.longTermOverstockQty60()).isEqualByComparingTo("50");
        assertThat(result.longTermOverstockQty90()).isEqualByComparingTo("40");
        assertThat(result.longTermOverstockRate90()).isEqualByComparingTo("40.00");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("LONG_TERM_OVERSTOCK_CAUTION");
        assertThat(result.apiRiskGrade()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("판매중지 LOT는 미할당 scope에서도 판매 가능 재고에서 제외한다")
    void unassignedScope_excludesSaleStoppedLotFromSellableStock() {
        RiskAssessmentInput.LotRiskItem stopped = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-STOPPED", BASE_DATE.plusDays(30), BASE_DATE.minusDays(1), BASE_DATE.minusDays(3), BigDecimal.valueOf(25), "SALE_STOPPED");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(25),
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(5), BASE_DATE,
                List.of(stopped), true, false));

        assertThat(result.availableQty()).isEqualByComparingTo("0");
        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("ZERO_AVAILABLE_STOCK", "LOT_SALE_STOPPED_EXCLUDED")
                .doesNotContain("LOT_SALE_STOPPED");
    }

    @Test
    @DisplayName("판매중지일이 7일 이내로 남은 판매 가능 LOT는 위험으로 판정한다")
    void upcomingSaleStopWithinSevenDays_isDanger() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-SALE-STOP-SOON", BASE_DATE.plusDays(365), BASE_DATE.plusDays(5),
                BASE_DATE.minusDays(3), BigDecimal.valueOf(100), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(lot), true, false));

        assertThat(result.availableQty()).isEqualByComparingTo("100");
        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("EXPECTED_DISPOSAL_DANGER");
        assertThat(result.primaryReason()).contains("30일 예상 폐기수량");
    }

    @Test
    @DisplayName("판매중지일이 30일 이내이고 예상 폐기율이 20퍼센트 이상이면 위험으로 판정한다")
    void upcomingSaleStopWithinThirtyDaysWithHighDisposalRate_isDanger() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-SALE-STOP-CAUTION", BASE_DATE.plusDays(365), BASE_DATE.plusDays(15),
                BASE_DATE.minusDays(3), BigDecimal.valueOf(100), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(lot), true, false));

        assertThat(result.dbRiskGrade()).isEqualTo("CRITICAL");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("EXPECTED_DISPOSAL_DANGER");
        assertThat(result.primaryReason()).contains("30일 예상 폐기수량");
    }

    @Test
    @DisplayName("30일 안에 판매가 종료되어도 예측수요로 전량 소진 가능하면 폐기 위험을 올리지 않는다")
    void saleEndingWithinThirtyDays_butForecastConsumesEverything_hasNoDisposalRisk() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-SOLD-BEFORE-END", BASE_DATE.plusDays(10), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(20), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(inputWithD14(
                BigDecimal.valueOf(100), BigDecimal.valueOf(15), BigDecimal.valueOf(30),
                BigDecimal.valueOf(60), BigDecimal.valueOf(10), List.of(lot), true, false));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("0");
        assertThat(result.expectedDisposalRate30()).isEqualByComparingTo("0");
        assertThat(result.nearestSaleEndDays()).isEqualTo(10);
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("SALE_END_CLEAR")
                .doesNotContain("EXPIRY_CRITICAL", "EXPECTED_DISPOSAL_DANGER", "EXPECTED_DISPOSAL_CAUTION");
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("30일 예상 폐기율이 20퍼센트 이상이면 위험으로 판정한다")
    void expectedDisposalRateAtLeastTwentyPercent_isDanger() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-HIGH-DISPOSAL", BASE_DATE.plusDays(20), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(50), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(inputWithD14(
                BigDecimal.valueOf(100), BigDecimal.TEN, BigDecimal.valueOf(20),
                BigDecimal.valueOf(25), BigDecimal.valueOf(5), List.of(lot), true, false));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("28.125");
        assertThat(result.expectedDisposalRate30()).isEqualByComparingTo("28.13");
        assertThat(result.nearestSaleEndDays()).isEqualTo(20);
        assertThat(result.reasons()).extracting(RiskReason::code).contains("EXPECTED_DISPOSAL_DANGER");
        assertThat(result.apiRiskGrade()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("판매종료 7일 이내이고 예상 폐기율이 5퍼센트 이상이면 위험으로 판정한다")
    void urgentSaleEndWithFivePercentDisposal_isDanger() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-URGENT-DISPOSAL", BASE_DATE.plusDays(5), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(15), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(inputWithD14(
                BigDecimal.valueOf(100), BigDecimal.valueOf(7), BigDecimal.valueOf(20),
                BigDecimal.valueOf(40), BigDecimal.valueOf(5), List.of(lot), true, false));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("10");
        assertThat(result.expectedDisposalRate30()).isEqualByComparingTo("10.00");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("EXPECTED_DISPOSAL_DANGER");
        assertThat(result.apiRiskGrade()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("예상 폐기율이 5퍼센트 이상 20퍼센트 미만이면 주의로 판정한다")
    void expectedDisposalRateBetweenFiveAndTwentyPercent_isCaution() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-CAUTION-DISPOSAL", BASE_DATE.plusDays(20), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(30), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(inputWithD14(
                BigDecimal.valueOf(100), BigDecimal.TEN, BigDecimal.valueOf(20),
                BigDecimal.valueOf(25), BigDecimal.valueOf(5), List.of(lot), true, false));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("8.125");
        assertThat(result.expectedDisposalRate30()).isEqualByComparingTo("8.13");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("EXPECTED_DISPOSAL_CAUTION");
        assertThat(result.apiRiskGrade()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("수요예측이 없는 미할당 재고는 30일 안에 판매 종료되는 수량 전부를 예상 폐기로 본다")
    void unassignedWithoutForecast_countsAllEndingStockAsExpectedDisposal() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-UNASSIGNED", BASE_DATE.plusDays(12), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(40), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(100),
                null, null, null, BigDecimal.valueOf(5), BASE_DATE,
                List.of(lot), false, false, BASE_DATE));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("40");
        assertThat(result.expectedDisposalRate30()).isEqualByComparingTo("40.00");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("EXPECTED_DISPOSAL_DANGER");
        assertThat(result.apiRiskGrade()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("미할당 재고는 수요예측과 안전재고가 없어도 30일 운영 위험이 없으면 양호다")
    void unassignedWithoutForecastOrSafety_isGoodWhenThirtyDayLotRiskIsClear() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-UNASSIGNED-CLEAR", BASE_DATE.plusDays(45), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(188), "SALE_STOPPED");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(188),
                null, null, null, null, BASE_DATE,
                List.of(lot), false, false, BASE_DATE));

        assertThat(result.dbRiskGrade()).isEqualTo("GOOD");
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("0");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("FORECAST_UNAVAILABLE", "WAREHOUSE_30_DAY_CLEAR")
                .doesNotContain("MEDIUM_TERM_DISPOSAL_CAUTION", "LIMITED_BASIS_MONITORING");
        assertThat(result.primaryReason())
                .isEqualTo("현재 판매 가능 재고는 188개이며, 판매 불가 재고와 30일 이내 판매 종료 예정 재고는 0개입니다.");
    }

    @Test
    @DisplayName("미할당 재고 LOT의 판매 종료 날짜가 없으면 양호를 확정하지 않는다")
    void unassignedWithoutLotEndDate_remainsNormal() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-UNASSIGNED-NO-DATE", null, null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(100), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(100),
                null, null, null, null, BASE_DATE,
                List.of(lot), false, false, BASE_DATE));

        assertThat(result.dbRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("WAREHOUSE_LOT_DATE_MISSING")
                .doesNotContain("WAREHOUSE_30_DAY_CLEAR");
        assertThat(result.primaryReason())
                .isEqualTo("판매 종료일을 확인할 수 없는 판매 가능 LOT 또는 재고 범위가 1건(100개) 있어 양호를 확정할 수 없습니다.");
    }

    @Test
    @DisplayName("미할당 재고의 판매 불가 비율은 5퍼센트와 20퍼센트 경계로 등급을 올린다")
    void unassignedExcludedStockRate_usesFiveAndTwentyPercentThresholds() {
        assertUnassignedExcludedRate("4", "NORMAL", "WAREHOUSE_UNSELLABLE_MONITORING");
        assertUnassignedExcludedRate("5", "WARNING", "WAREHOUSE_UNSELLABLE_WARNING");
        assertUnassignedExcludedRate("20", "CRITICAL", "WAREHOUSE_UNSELLABLE_CRITICAL");
    }

    @Test
    @DisplayName("예상 폐기는 D+30을 포함하고 D+31은 제외한다")
    void expectedDisposal_includesDayThirtyAndExcludesDayThirtyOne() {
        RiskAssessmentInput.LotRiskItem dayThirty = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-D30", BASE_DATE.plusDays(30), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(20), "AVAILABLE");
        RiskAssessmentInput.LotRiskItem dayThirtyOne = new RiskAssessmentInput.LotRiskItem(
                "2", "LOT-D31", BASE_DATE.plusDays(31), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(80), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(100),
                null, null, null, BigDecimal.valueOf(5), BASE_DATE,
                List.of(dayThirty, dayThirtyOne), false, false, BASE_DATE));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("20");
        assertThat(result.nearestSaleEndDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("소비기한과 판매중지일 중 더 빠른 날짜를 판매 종료일로 사용한다")
    void expectedDisposal_usesEarlierOfExpiryAndSaleStopDate() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-EFFECTIVE-END", BASE_DATE.plusDays(20), BASE_DATE.plusDays(5),
                BASE_DATE.minusDays(3), BigDecimal.TEN, "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(100),
                null, null, null, BigDecimal.valueOf(5), BASE_DATE,
                List.of(lot), false, false, BASE_DATE));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("10");
        assertThat(result.nearestSaleEndDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("여러 판매 종료일 중 누적 잔량의 최댓값을 예상 폐기로 사용한다")
    void expectedDisposal_usesMaximumCumulativeResidualAcrossDeadlines() {
        RiskAssessmentInput.LotRiskItem daySeven = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-D7", BASE_DATE.plusDays(7), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(20), "AVAILABLE");
        RiskAssessmentInput.LotRiskItem dayFourteen = new RiskAssessmentInput.LotRiskItem(
                "2", "LOT-D14", BASE_DATE.plusDays(14), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(30), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(inputWithD14(
                BigDecimal.valueOf(100), BigDecimal.valueOf(5), BigDecimal.valueOf(40),
                BigDecimal.valueOf(40), BigDecimal.valueOf(5),
                List.of(daySeven, dayFourteen), true, false));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("15");
        assertThat(result.expectedDisposalRate30()).isEqualByComparingTo("15.00");
        assertThat(result.nearestSaleEndDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("D+7과 D+14 경계 전후에서 각 구간의 누적 수요를 보간한다")
    void expectedDisposal_interpolatesEveryForecastSegmentAtItsBoundaries() {
        assertExpectedDisposalAtDay(1, "98");
        assertExpectedDisposalAtDay(7, "86");
        assertExpectedDisposalAtDay(8, "82");
        assertExpectedDisposalAtDay(14, "58");
        assertExpectedDisposalAtDay(15, "56");
        assertExpectedDisposalAtDay(30, "26");
    }

    @Test
    @DisplayName("판매처가 있어도 사용할 수 있는 수요예측이 없으면 종료 LOT 전량을 예상 폐기로 본다")
    void assignedWithoutUsableForecast_countsAllEndingStockAsExpectedDisposal() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-ASSIGNED-NO-FORECAST", BASE_DATE.plusDays(12), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(40), "AVAILABLE");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(100),
                null, null, null, BigDecimal.valueOf(5), BASE_DATE,
                List.of(lot), false, false, BASE_DATE));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo("40");
        assertThat(result.expectedDisposalRate30()).isEqualByComparingTo("40.00");
        assertThat(result.shortageQty30()).isNull();
    }

    @Test
    @DisplayName("오래된 예측 기준일은 판정에서 제외한다")
    void staleForecast_isNotUsable() {
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(80),
                BigDecimal.valueOf(80), BigDecimal.valueOf(80), BigDecimal.valueOf(30), BASE_DATE.minusDays(15),
                List.of(), true, true, BASE_DATE));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("GOOD");
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
        assertThat(result.projectedD7()).isNull();
        assertThat(result.forecastUsability()).isEqualTo(RiskRuleEngine.FORECAST_MISSING);
        assertThat(result.reasons()).extracting(RiskReason::code).contains("FORECAST_UNAVAILABLE");
    }

    @Test
    @DisplayName("오래된 예측을 사용해도 경과 LOT는 현재 판정일 기준으로 판매 가능 재고에서 제외한다")
    void staleForecast_excludesExpiredLotUsingCurrentAssessmentDate() {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-OLD-FORECAST", BASE_DATE.minusDays(1), null, BASE_DATE.minusDays(10), BigDecimal.TEN
        );
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30),
                BASE_DATE.minusDays(15), List.of(lot), true, true, BASE_DATE
        ));

        assertThat(result.availableQty()).isEqualByComparingTo("90");
        assertThat(result.dbRiskGrade()).isEqualTo("GOOD");
        assertThat(result.reasons()).extracting(RiskReason::code).contains("LOT_EXPIRED_EXCLUDED");
        assertThat(result.nearestExpiryDays()).isNull();
        assertThat(result.maxHoldingDays()).isNull();
    }

    @Test
    @DisplayName("예측이 없어도 안전재고 정책과 LOT가 양호하면 양호로 판정한다")
    void missingForecast_withSafetyPolicyAndHealthyLots_isSafe() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                null, null, BigDecimal.valueOf(30), List.of(), false, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("GOOD");
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
        assertThat(result.shortageQty30()).isNull();
        assertThat(result.projectedD7()).isNull();
        assertThat(result.reasons()).extracting(RiskReason::code).contains("FORECAST_UNAVAILABLE");
        assertThat(result.primaryReason()).contains("현재 판매 가능 재고 100개가 안전재고 30개를 충족");
    }

    @Test
    @DisplayName("예측과 안전재고 정책이 모두 없으면 양호를 확정하지 않는다")
    void missingForecast_withoutSafetyPolicy_remainsNormal() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                null, null, null, List.of(), false, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.apiRiskGrade()).isEqualTo("NORMAL");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("FORECAST_UNAVAILABLE", "LIMITED_BASIS_MONITORING");
        assertThat(result.primaryReason())
                .isEqualTo("현재 판매 가능 재고는 100개이며, 현재 확인 가능한 기준에서 보통으로 판정했습니다.");
    }

    @Test
    @DisplayName("예측이 없어도 현재 재고가 안전재고 미만이면 서버 룰로 주의 판정한다")
    void missingForecast_currentUnderSafety_isCaution() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(20),
                null, null, BigDecimal.valueOf(30), List.of(), false, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("WARNING");
        assertThat(result.apiRiskGrade()).isEqualTo("WARNING");
        assertThat(result.reasons()).extracting(RiskReason::code)
                .contains("FORECAST_UNAVAILABLE", "CURRENT_UNDER_SAFETY");
        assertThat(result.primaryReason()).contains("현재 판매 가능 재고 20개가 안전재고 30개보다 10개 부족합니다");
    }

    @Test
    @DisplayName("누적 예측값이 잘못되면 예측 규칙만 제외하고 판정한다")
    void decreasingForecast_skipsInvalidForecastRules() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(80), BigDecimal.valueOf(70), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.dbRiskGrade()).isEqualTo("GOOD");
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
        assertThat(result.shortageQty30()).isNull();
        assertThat(result.reasons()).extracting(RiskReason::code).contains("FORECAST_INVALID");
        assertThat(result.primaryReason()).contains("현재 판매 가능 재고 100개가 안전재고 30개를 충족");
    }

    @Test
    @DisplayName("예측 행이 있어도 필수 D+60·D+90 값이 비어 있으면 잘못된 예측으로 판정한다")
    void forecastWithMissingExtendedHorizon_isInvalid() {
        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "GREETING", BigDecimal.valueOf(100),
                BigDecimal.TEN, BigDecimal.valueOf(20), BigDecimal.valueOf(30),
                null, BigDecimal.valueOf(50), BigDecimal.TEN, BASE_DATE,
                List.of(), true, false, BASE_DATE));

        assertThat(result.forecastUsability()).isEqualTo(RiskRuleEngine.FORECAST_INVALID);
        assertThat(result.shortageQty30()).isNull();
        assertThat(result.projectedD60()).isNull();
        assertThat(result.reasons()).extracting(RiskReason::code).contains("FORECAST_INVALID");
    }

    @Test
    @DisplayName("수요예측·재고·정책·LOT만으로 판정한다")
    void usesOnlyForecastInventoryPolicyAndLot() {
        RiskAssessmentResult result = engine.evaluate(input(BigDecimal.valueOf(100),
                BigDecimal.valueOf(20), BigDecimal.valueOf(80), BigDecimal.valueOf(30), List.of(), true, false));

        assertThat(result.assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(result.apiRiskGrade()).isEqualTo("GOOD");
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
        BigDecimal predictedQtyD14 = compatibleD14(predictedQtyD7, predictedQtyD30);
        BigDecimal extended = extendedForecast(onHandQty, predictedQtyD30);
        return new RiskAssessmentInput(
                "SKU-001", "GREETING", onHandQty,
                predictedQtyD7, predictedQtyD14, predictedQtyD30, extended, extended,
                safetyStockQty, BASE_DATE, lots, forecastAvailable, forecastStale, BASE_DATE
        );
    }

    private RiskAssessmentInput inputWithD14(
            BigDecimal onHandQty,
            BigDecimal predictedQtyD7,
            BigDecimal predictedQtyD14,
            BigDecimal predictedQtyD30,
            BigDecimal safetyStockQty,
            List<RiskAssessmentInput.LotRiskItem> lots,
            boolean forecastAvailable,
            boolean forecastStale
    ) {
        BigDecimal extended = extendedForecast(onHandQty, predictedQtyD30);
        return new RiskAssessmentInput(
                "SKU-001", "GREETING", onHandQty,
                predictedQtyD7, predictedQtyD14, predictedQtyD30, extended, extended, safetyStockQty,
                BASE_DATE, lots, forecastAvailable, forecastStale, BASE_DATE
        );
    }

    private BigDecimal compatibleD14(BigDecimal predictedQtyD7, BigDecimal predictedQtyD30) {
        if (predictedQtyD7 == null || predictedQtyD30 == null) return null;
        if (predictedQtyD30.compareTo(predictedQtyD7) < 0) return predictedQtyD7;
        return predictedQtyD7.add(predictedQtyD30.subtract(predictedQtyD7)
                .multiply(BigDecimal.valueOf(7))
                .divide(BigDecimal.valueOf(23), 6, java.math.RoundingMode.HALF_UP));
    }

    private BigDecimal extendedForecast(BigDecimal onHandQty, BigDecimal predictedQtyD30) {
        if (predictedQtyD30 == null) return null;
        if (onHandQty == null) return predictedQtyD30;
        return onHandQty.max(predictedQtyD30);
    }

    private void assertUnassignedExcludedRate(String excludedQty, String expectedGrade, String expectedReasonCode) {
        BigDecimal excluded = new BigDecimal(excludedQty);
        RiskAssessmentInput.LotRiskItem stopped = new RiskAssessmentInput.LotRiskItem(
                "stopped-" + excludedQty, "LOT-STOPPED-" + excludedQty,
                BASE_DATE.plusDays(90), BASE_DATE.minusDays(1), BASE_DATE.minusDays(10), excluded, "AVAILABLE");
        RiskAssessmentInput.LotRiskItem available = new RiskAssessmentInput.LotRiskItem(
                "available-" + excludedQty, "LOT-AVAILABLE-" + excludedQty,
                BASE_DATE.plusDays(45), null, BASE_DATE.minusDays(3),
                BigDecimal.valueOf(100).subtract(excluded), "SALE_STOPPED");

        RiskAssessmentResult result = engine.evaluate(new RiskAssessmentInput(
                "SKU-001", "UNASSIGNED", BigDecimal.valueOf(100),
                null, null, null, null, BASE_DATE,
                List.of(stopped, available), false, false, BASE_DATE));

        assertThat(result.dbRiskGrade()).isEqualTo(expectedGrade);
        assertThat(result.reasons()).extracting(RiskReason::code).contains(expectedReasonCode);
        assertThat(result.primaryReason()).contains("판매 불가 재고 " + excludedQty + "개");
    }

    private void assertExpectedDisposalAtDay(int day, String expectedQuantity) {
        RiskAssessmentInput.LotRiskItem lot = new RiskAssessmentInput.LotRiskItem(
                String.valueOf(day), "LOT-D" + day, BASE_DATE.plusDays(day), null,
                BASE_DATE.minusDays(3), BigDecimal.valueOf(100), "AVAILABLE");
        RiskAssessmentResult result = engine.evaluate(inputWithD14(
                BigDecimal.valueOf(200), BigDecimal.valueOf(14), BigDecimal.valueOf(42),
                BigDecimal.valueOf(74), BigDecimal.valueOf(5), List.of(lot), true, false));

        assertThat(result.expectedDisposalQty30()).isEqualByComparingTo(expectedQuantity);
    }

    private RiskAssessmentInput.LotRiskItem lot(LocalDate expiryDate, LocalDate saleStopDate, LocalDate receivedDate) {
        return new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-01", expiryDate, saleStopDate, receivedDate, BigDecimal.TEN
        );
    }
}
