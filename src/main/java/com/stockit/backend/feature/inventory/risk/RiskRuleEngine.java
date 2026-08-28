package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/**
 * 재고 위험의 모든 영역을 독립적으로 계산하는 순수 규칙 엔진입니다.
 * LOT 원천 상태는 사용하지 않고 기준일·날짜·통합 잔량으로 상태를 결정하며,
 * 대표 사유는 고정된 심각도/업무 우선순위로 선택합니다.
 */
@Component
public class RiskRuleEngine {

    public static final String RULE_VERSION = "v1.8.0";
    public static final String FORECAST_VALID = "VALID";
    public static final String FORECAST_MISSING = "MISSING";
    public static final String FORECAST_INVALID = "INVALID";

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int DISPOSAL_HORIZON_DAYS = 30;
    private static final int LONG_HORIZON_DAYS = 90;
    private static final int URGENT_DAYS = 7;
    private static final BigDecimal DANGER_RATE = BigDecimal.valueOf(20);
    private static final BigDecimal CAUTION_RATE = BigDecimal.valueOf(5);
    private static final BigDecimal DANGER_STOCKOUT_DAYS = BigDecimal.valueOf(14);
    private static final BigDecimal CAUTION_STOCKOUT_DAYS = BigDecimal.valueOf(30);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private static final Map<String, Integer> REASON_PRIORITY = Map.ofEntries(
            Map.entry("DATA_MISSING", 1), Map.entry("ZERO_AVAILABLE_STOCK", 2),
            Map.entry("STOCKOUT_WITHIN_14_DAYS", 3), Map.entry("PROJECTED_UNDER_SAFETY_D7", 4),
            Map.entry("EXPECTED_DISPOSAL_DANGER", 5), Map.entry("CURRENT_UNDER_SAFETY", 6),
            Map.entry("STOCKOUT_WITHIN_30_DAYS", 7), Map.entry("EXPECTED_DISPOSAL_CAUTION", 8),
            Map.entry("MEDIUM_TERM_DISPOSAL_CAUTION", 9), Map.entry("LONG_TERM_OVERSTOCK_CAUTION", 10),
            Map.entry("EXPECTED_DISPOSAL_MONITORING", 11), Map.entry("MEDIUM_TERM_DISPOSAL_MONITORING", 12),
            Map.entry("LONG_TERM_OVERSTOCK_MONITORING", 13), Map.entry("LONG_TERM_CLEARING_MONITORING", 14),
            Map.entry("WAREHOUSE_UNSELLABLE_CRITICAL", 5), Map.entry("WAREHOUSE_UNSELLABLE_WARNING", 8),
            Map.entry("WAREHOUSE_UNSELLABLE_MONITORING", 11), Map.entry("WAREHOUSE_LOT_DATE_MISSING", 14),
            Map.entry("LIMITED_BASIS_MONITORING", 15), Map.entry("WAREHOUSE_30_DAY_CLEAR", 16),
            Map.entry("SALE_END_CLEAR", 17), Map.entry("CURRENT_POLICY_CLEAR", 18),
            Map.entry("OPTIMAL_STOCK", 19),
            Map.entry("FORECAST_UNAVAILABLE", 90), Map.entry("FORECAST_INVALID", 91),
            Map.entry("LOT_EXPIRED_EXCLUDED", 92), Map.entry("LOT_SALE_STOPPED_EXCLUDED", 93),
            Map.entry("LOT_DEPLETED_EXCLUDED", 94)
    );

    private final Clock clock;

    public RiskRuleEngine() {
        this(Clock.system(BUSINESS_ZONE));
    }

    public RiskRuleEngine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RiskAssessmentResult evaluate(RiskAssessmentInput input) {
        return evaluate(input, Instant.now(clock));
    }

    /** 동기화 run이 고정한 판정시각을 주입해 LOT·등급·사유 스냅샷의 시각을 일치시킵니다. */
    public RiskAssessmentResult evaluate(RiskAssessmentInput input, Instant assessedAt) {
        if (input == null) {
            throw new IllegalArgumentException("risk assessment input is required");
        }
        if (assessedAt == null) {
            throw new IllegalArgumentException("assessedAt is required");
        }
        LocalDate baseDate = input.baseDate() == null ? LocalDate.now(clock) : input.baseDate();
        LocalDate assessmentDate = input.assessmentDate() == null ? baseDate : input.assessmentDate();
        validateNonNegative(input);

        boolean inventoryMissing = input.onHandQty() == null;
        BigDecimal onHandQty = inventoryMissing ? ZERO : input.onHandQty();
        BigDecimal reservedQty = input.reservedQty() == null ? ZERO : input.reservedQty();
        BigDecimal currentQty = onHandQty.add(reservedQty);

        List<LotView> lotViews = resolveLots(input.lots(), assessmentDate);
        BigDecimal excludedOnHandQty = lotViews.stream()
                .filter(view -> view.status() != LotStatus.AVAILABLE)
                .map(LotView::onHandQty).reduce(ZERO, BigDecimal::add);
        BigDecimal availableQty = onHandQty.subtract(excludedOnHandQty).max(ZERO);

        String forecastUsability = forecastUsability(input);
        boolean forecastUsable = FORECAST_VALID.equals(forecastUsability);
        BigDecimal f7 = forecastUsable ? input.predictedQtyD7() : null;
        BigDecimal f30 = forecastUsable ? input.predictedQtyD30() : null;
        BigDecimal projectedD7 = f7 == null ? null : availableQty.subtract(f7).max(ZERO);
        BigDecimal shortageQty30 = f30 == null ? null : f30.subtract(availableQty).max(ZERO);

        ExpectedDisposal disposal = calculateDisposal(lotViews, input, assessmentDate, availableQty, forecastUsable);
        BigDecimal safetyStock = input.safetyStockQty();
        BigDecimal safetyGap = calculateSafetyGap(availableQty, projectedD7, safetyStock);
        BigDecimal projectedD60 = null;
        BigDecimal projectedD90 = null;
        BigDecimal overstockQty60 = null;
        BigDecimal overstockQty90 = null;
        BigDecimal overstockRate90 = null;
        if (forecastUsable && input.extendedForecastProvided()) {
            projectedD60 = remainingSellable(availableQty, predictedQtyAtDay(input, 60), disposal.disposalAt60());
            projectedD90 = remainingSellable(availableQty, predictedQtyAtDay(input, 90), disposal.disposalAt90());
            BigDecimal basis = safetyStock == null ? ZERO : safetyStock;
            overstockQty60 = projectedD60.subtract(basis).max(ZERO);
            overstockQty90 = projectedD90.subtract(basis).max(ZERO);
            overstockRate90 = percentage(overstockQty90, availableQty);
        }

        List<RiskReason> reasons = new ArrayList<>();
        addLotExclusionReasons(reasons, lotViews);
        addForecastBasisReason(reasons, forecastUsability);

        // 모든 위험 영역을 평가한 뒤 마지막에 최대 심각도와 대표 사유를 고릅니다.
        if (inventoryMissing) {
            reasons.add(reason("DATA_MISSING",
                    "현재 재고수량을 확인할 수 없어 판매 가능 재고와 부족 위험을 판정할 수 없습니다.",
                    "CRITICAL", "onHandQty=null"));
        } else if (availableQty.signum() == 0) {
            reasons.add(reason("ZERO_AVAILABLE_STOCK",
                    "현재 재고 " + quantity(currentQty) + "개 중 판매 불가 LOT의 재고 "
                            + quantity(excludedOnHandQty) + "개를 제외한 판매 가능 재고가 0개입니다.",
                    "CRITICAL", "currentQty=" + currentQty + ", excludedOnHandQty=" + excludedOnHandQty));
        }

        if (f30 != null && f30.compareTo(availableQty) > 0) {
            BigDecimal coverageDays = stockCoverageDays(availableQty, f30);
            if (coverageDays.compareTo(DANGER_STOCKOUT_DAYS) <= 0) {
                reasons.add(reason("STOCKOUT_WITHIN_14_DAYS",
                        "현재 판매 가능 재고 " + quantity(availableQty) + "개와 30일 예측수요 "
                                + quantity(f30) + "개 기준으로 약 " + decimal(coverageDays)
                                + "일 후 재고가 소진될 것으로 예상됩니다.",
                        "CRITICAL", "coverageDays=" + coverageDays));
            } else if (coverageDays.compareTo(CAUTION_STOCKOUT_DAYS) < 0) {
                reasons.add(reason("STOCKOUT_WITHIN_30_DAYS",
                        "30일 예측수요 " + quantity(f30) + "개가 판매 가능 재고 "
                                + quantity(availableQty) + "개보다 " + quantity(shortageQty30)
                                + "개 많아 약 " + decimal(coverageDays)
                                + "일 후 재고가 소진될 것으로 예상됩니다.",
                        "WARNING", "coverageDays=" + coverageDays));
            }
        }

        if (safetyStock != null && availableQty.compareTo(safetyStock) < 0) {
            reasons.add(reason("CURRENT_UNDER_SAFETY",
                    "현재 판매 가능 재고 " + quantity(availableQty) + "개가 안전재고 "
                            + quantity(safetyStock) + "개보다 " + quantity(safetyStock.subtract(availableQty)) + "개 부족합니다.",
                    "WARNING", "availableQty=" + availableQty + ", safetyStockQty=" + safetyStock));
        }
        // A<S이면 현재 부족만 평가하고 D+7 부족을 중복해서 critical로 올리지 않습니다.
        if (safetyStock != null && projectedD7 != null && availableQty.compareTo(safetyStock) >= 0
                && projectedD7.compareTo(safetyStock) < 0) {
            reasons.add(reason("PROJECTED_UNDER_SAFETY_D7",
                    "7일 후 예상 재고 " + quantity(projectedD7) + "개가 안전재고 " + quantity(safetyStock)
                            + "개보다 " + quantity(safetyStock.subtract(projectedD7)) + "개 부족할 것으로 예상됩니다.",
                    "CRITICAL", "projectedD7=" + projectedD7 + ", safetyStockQty=" + safetyStock));
        }

        addDisposalReasons(reasons, disposal, assessmentDate, availableQty);
        boolean warehouseScope = isUnassignedScope(input);
        // 판매처 미할당 재고는 수요예측을 억지로 연결하지 않고 향후 30일 운영 위험만 봅니다.
        // 따라서 D+31~90의 잠재 수량은 현재 등급을 올리지 않습니다.
        if (!warehouseScope) {
            addMediumTermReasons(reasons, disposal, assessmentDate, availableQty);
        }
        addLongTermReasons(reasons, projectedD60, overstockQty60, overstockQty90, overstockRate90, availableQty,
                safetyStock);
        addWarehouseLotRules(reasons, input, lotViews, currentQty, availableQty, excludedOnHandQty, disposal);

        boolean hasPositiveReason = reasons.stream().anyMatch(item -> severityRank(item.severity()) >= 1);
        if (!hasPositiveReason) {
            if (!forecastUsable && safetyStock == null
                    && isWarehouseThirtyDayClear(input, lotViews, availableQty, excludedOnHandQty, disposal)) {
                reasons.add(reason("WAREHOUSE_30_DAY_CLEAR",
                        warehouseClearMessage(availableQty, excludedOnHandQty, disposal.quantity()),
                        "GOOD", "availableQty=" + availableQty + ", excludedOnHandQty=" + excludedOnHandQty
                                + ", expectedDisposalQty30=" + disposal.quantity()));
            } else if (!forecastUsable && safetyStock == null) {
                reasons.add(reason("LIMITED_BASIS_MONITORING", limitedBasisMessage(availableQty, excludedOnHandQty),
                        "NORMAL", "forecastUsability=" + forecastUsability + ", safetyStockQty=null"));
            } else if (safetyStock != null && !forecastUsable) {
                reasons.add(reason("CURRENT_POLICY_CLEAR", currentPolicyClearMessage(availableQty, safetyStock, excludedOnHandQty),
                        "GOOD", "safetyStockQty=" + safetyStock));
            } else {
                reasons.add(reason("OPTIMAL_STOCK", optimalStockMessage(availableQty, f30, safetyStock), "GOOD",
                        "availableQty=" + availableQty + ", expectedDisposalQty30=" + disposal.quantity()));
            }
        }

        reasons.sort(RiskRuleEngine::compareReasons);
        RiskReason primary = reasons.stream().filter(item -> severityRank(item.severity()) >= 1).findFirst()
                .orElseGet(() -> reason("OPTIMAL_STOCK", "현재 재고 상태를 확인할 수 있습니다.", "GOOD", null));
        String dbGrade = toDbGrade(primary.severity());
        return new RiskAssessmentResult(
                "ASSESSED", dbGrade, dbGrade, primary.message(), List.copyOf(reasons), availableQty,
                shortageQty30, safetyGap, projectedD7, safetyStock, disposal.quantity(), disposal.rate(),
                disposal.nearestSaleEndDays(), nearestExpiryDays(lotViews, assessmentDate), maxHoldingDays(lotViews, assessmentDate),
                baseDate, assessedAt, RULE_VERSION, forecastUsability, projectedD60, projectedD90,
                disposal.quantity90(), disposal.mediumTermQuantity90(), disposal.mediumTermRate90(),
                disposal.mediumTermSaleEndDays(), overstockQty60, overstockQty90, overstockRate90);
    }

    private static void validateNonNegative(RiskAssessmentInput input) {
        requireNonNegative(input.onHandQty(), "on_hand_qty");
        requireNonNegative(input.reservedQty(), "reserved_qty");
        requireNonNegative(input.safetyStockQty(), "safetyStockQty");
        if (input.lots() == null) return;
        for (RiskAssessmentInput.LotRiskItem lot : input.lots()) {
            if (lot == null) throw new IllegalArgumentException("lot must not be null");
            requireNonNegative(lot.quantity(), "lot quantity");
            requireNonNegative(lot.reservedQty(), "lot reserved quantity");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value != null && value.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static String forecastUsability(RiskAssessmentInput input) {
        String supplied = input.normalizedForecastUsability();
        if (FORECAST_MISSING.equals(supplied)) return FORECAST_MISSING;
        if (FORECAST_INVALID.equals(supplied)) return FORECAST_INVALID;
        // 기준일보다 이틀 이상 오래된 예측은 품질 오류(비단조·음수)가 아니라
        // 현재 판정에 사용할 수 없는 입력으로 취급합니다. LOT/재고/안전재고
        // 규칙은 계속 평가하되, 미래 수요 기반 규칙만 비활성화합니다.
        if (input.forecastStale()) return FORECAST_MISSING;
        if (!input.forecastAvailable()) return FORECAST_MISSING;
        if (input.predictedQtyD7() == null || input.predictedQtyD14() == null || input.predictedQtyD30() == null) return FORECAST_INVALID;
        List<BigDecimal> values;
        if (input.extendedForecastProvided()) {
            if (input.predictedQtyD60() == null || input.predictedQtyD90() == null) return FORECAST_INVALID;
            values = List.of(input.predictedQtyD7(), input.predictedQtyD14(), input.predictedQtyD30(),
                    input.predictedQtyD60(), input.predictedQtyD90());
        } else {
            values = List.of(input.predictedQtyD7(), input.predictedQtyD14(), input.predictedQtyD30());
        }
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null || values.get(i).compareTo(ZERO) < 0
                    || (i > 0 && values.get(i).compareTo(values.get(i - 1)) < 0)) return FORECAST_INVALID;
        }
        return FORECAST_VALID;
    }

    private static List<LotView> resolveLots(List<RiskAssessmentInput.LotRiskItem> lots, LocalDate date) {
        if (lots == null || lots.isEmpty()) return List.of();
        Map<String, BigDecimal> integratedByLot = new HashMap<>();
        for (RiskAssessmentInput.LotRiskItem lot : lots) {
            integratedByLot.merge(lotKey(lot), nullableZero(lot.quantity()).add(nullableZero(lot.reservedQty())), BigDecimal::add);
        }
        List<LotView> result = new ArrayList<>();
        for (RiskAssessmentInput.LotRiskItem lot : lots) {
            BigDecimal integrated = integratedByLot.getOrDefault(lotKey(lot), ZERO);
            result.add(new LotView(lot, resolveStatus(lot, date, integrated), nullableZero(lot.quantity()),
                    nullableZero(lot.reservedQty()), integrated));
        }
        result.sort(Comparator.comparing(LotView::sortKey));
        return List.copyOf(result);
    }

    private static String lotKey(RiskAssessmentInput.LotRiskItem lot) {
        if (lot.lotId() != null && !lot.lotId().isBlank()) return "id:" + lot.lotId();
        return "value:" + lot.lotNumber() + "|" + lot.expiryDate() + "|" + lot.saleStopDate() + "|" + lot.receivedDate();
    }

    private static LotStatus resolveStatus(RiskAssessmentInput.LotRiskItem lot, LocalDate date, BigDecimal integrated) {
        boolean expiryReached = lot.expiryDate() != null && !lot.expiryDate().isAfter(date);
        boolean saleStopReached = lot.saleStopDate() != null && !lot.saleStopDate().isAfter(date);
        boolean expiryWins = expiryReached && (lot.saleStopDate() == null || !lot.expiryDate().isAfter(lot.saleStopDate()));
        boolean saleStopWins = saleStopReached && (lot.expiryDate() == null || lot.saleStopDate().isBefore(lot.expiryDate()));
        if (expiryWins) return LotStatus.EXPIRED;
        if (saleStopWins) return LotStatus.SALE_STOPPED;
        if (integrated.signum() == 0) return LotStatus.DEPLETED;
        return LotStatus.AVAILABLE;
    }

    private static void addLotExclusionReasons(List<RiskReason> reasons, List<LotView> lots) {
        for (LotView lot : lots) {
            String code = switch (lot.status()) {
                case EXPIRED -> "LOT_EXPIRED_EXCLUDED";
                case SALE_STOPPED -> "LOT_SALE_STOPPED_EXCLUDED";
                case DEPLETED -> "LOT_DEPLETED_EXCLUDED";
                case AVAILABLE -> null;
            };
            if (code == null || lot.onHandQty().signum() == 0) continue;
            String label = lot.status() == LotStatus.EXPIRED ? "소비기한이 지난"
                    : lot.status() == LotStatus.SALE_STOPPED ? "판매중지된" : "소진된";
            reasons.add(reason(code, label + " LOT의 재고를 판매 가능 재고에서 제외했습니다.", "INFO",
                    "lotId=" + lot.item().lotId() + ", lotStatus=" + lot.status() + ", integratedQty=" + lot.integratedBalance()));
        }
    }

    private static void addForecastBasisReason(List<RiskReason> reasons, String usability) {
        if (FORECAST_MISSING.equals(usability)) reasons.add(reason("FORECAST_UNAVAILABLE", "수요예측을 확인할 수 없는 상황입니다.", "INFO", usability));
        if (FORECAST_INVALID.equals(usability)) reasons.add(reason("FORECAST_INVALID", "수요예측 값이 유효하지 않은 상황입니다.", "INFO", usability));
    }

    /**
     * 판매처 미할당 재고는 안전재고·수요예측 대신 확정된 LOT 사실로만 평가합니다.
     * 이미 판매 불가가 된 수량은 현재 재고 대비 비율로, 날짜 누락은 양호 확정 제한으로 반영합니다.
     */
    private static void addWarehouseLotRules(
            List<RiskReason> reasons,
            RiskAssessmentInput input,
            List<LotView> lots,
            BigDecimal currentQty,
            BigDecimal availableQty,
            BigDecimal excludedQty,
            ExpectedDisposal disposal
    ) {
        if (!isUnassignedScope(input)) return;

        if (excludedQty.signum() > 0) {
            BigDecimal excludedRate = rawPercentage(excludedQty, currentQty);
            String code;
            String severity;
            if (excludedRate.compareTo(DANGER_RATE) >= 0) {
                code = "WAREHOUSE_UNSELLABLE_CRITICAL";
                severity = "CRITICAL";
            } else if (excludedRate.compareTo(CAUTION_RATE) >= 0) {
                code = "WAREHOUSE_UNSELLABLE_WARNING";
                severity = "WARNING";
            } else {
                code = "WAREHOUSE_UNSELLABLE_MONITORING";
                severity = "NORMAL";
            }
            reasons.add(reason(code,
                    "현재 재고 " + quantity(currentQty) + "개 중 판매 불가 재고 " + quantity(excludedQty)
                            + "개를 제외했으며, 판매 불가 비율은 " + rate(excludedRate) + "%입니다.",
                    severity, "currentQty=" + currentQty + ", excludedOnHandQty=" + excludedQty
                            + ", excludedRate=" + excludedRate));
        }

        if (availableQty.signum() <= 0 || disposal.quantity().signum() > 0) return;
        List<LotView> positiveAvailableLots = positiveAvailableLots(lots);
        long missingDateCount = positiveAvailableLots.stream()
                .filter(lot -> effectiveSaleEndDate(lot.item()) == null)
                .count();
        BigDecimal coveredQty = positiveAvailableLots.stream()
                .map(LotView::onHandQty)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal uncoveredQty = availableQty.subtract(coveredQty).max(ZERO);
        if (missingDateCount > 0 || uncoveredQty.signum() > 0 || positiveAvailableLots.isEmpty()) {
            long missingScopeCount = missingDateCount + (uncoveredQty.signum() > 0 || positiveAvailableLots.isEmpty() ? 1 : 0);
            BigDecimal missingDateQty = positiveAvailableLots.stream()
                    .filter(lot -> effectiveSaleEndDate(lot.item()) == null)
                    .map(LotView::onHandQty)
                    .reduce(uncoveredQty, BigDecimal::add);
            reasons.add(reason("WAREHOUSE_LOT_DATE_MISSING",
                    "판매 종료일을 확인할 수 없는 판매 가능 LOT 또는 재고 범위가 " + missingScopeCount
                            + "건(" + quantity(missingDateQty) + "개) 있어 양호를 확정할 수 없습니다.",
                    "NORMAL", "missingScopeCount=" + missingScopeCount + ", missingDateQty=" + missingDateQty));
        }
    }

    private static boolean isWarehouseThirtyDayClear(
            RiskAssessmentInput input,
            List<LotView> lots,
            BigDecimal availableQty,
            BigDecimal excludedQty,
            ExpectedDisposal disposal
    ) {
        if (!isUnassignedScope(input)
                || availableQty.signum() <= 0
                || excludedQty.signum() > 0
                || disposal.quantity().signum() > 0) {
            return false;
        }
        List<LotView> positiveAvailableLots = positiveAvailableLots(lots);
        if (positiveAvailableLots.isEmpty()
                || positiveAvailableLots.stream().anyMatch(lot -> effectiveSaleEndDate(lot.item()) == null)) {
            return false;
        }
        BigDecimal coveredQty = positiveAvailableLots.stream()
                .map(LotView::onHandQty)
                .reduce(ZERO, BigDecimal::add);
        return coveredQty.compareTo(availableQty) == 0;
    }

    private static List<LotView> positiveAvailableLots(List<LotView> lots) {
        return lots.stream()
                .filter(lot -> lot.status() == LotStatus.AVAILABLE && lot.onHandQty().signum() > 0)
                .toList();
    }

    private static boolean isUnassignedScope(RiskAssessmentInput input) {
        return input.salesPointCode() != null && "UNASSIGNED".equalsIgnoreCase(input.salesPointCode().trim());
    }

    private static void addDisposalReasons(List<RiskReason> reasons, ExpectedDisposal disposal, LocalDate date, BigDecimal available) {
        if (disposal.quantity().signum() <= 0) {
            if (disposal.nearestSaleEndDays() != null) reasons.add(reason("SALE_END_CLEAR",
                    disposal.nearestSaleEndDays() + "일 후 판매 종료되는 LOT가 있지만 현재 수요예측 기준으로 기한 내 전량 소진 가능하며, 30일 예상 폐기수량은 0개입니다.",
                    "GOOD", "nearestSaleEndDays=" + disposal.nearestSaleEndDays()));
            return;
        }
        boolean danger = disposal.rawRate().compareTo(DANGER_RATE) >= 0
                || (disposal.nearestSaleEndDays() != null && disposal.nearestSaleEndDays() <= URGENT_DAYS
                && disposal.rawRate().compareTo(CAUTION_RATE) >= 0);
        boolean caution = disposal.rawRate().compareTo(CAUTION_RATE) >= 0
                || (disposal.nearestSaleEndDays() != null && disposal.nearestSaleEndDays() <= URGENT_DAYS);
        String code = danger ? "EXPECTED_DISPOSAL_DANGER" : caution ? "EXPECTED_DISPOSAL_CAUTION" : "EXPECTED_DISPOSAL_MONITORING";
        String severity = danger ? "CRITICAL" : caution ? "WARNING" : "NORMAL";
        LocalDate deadline = date.plusDays(disposal.nearestSaleEndDays());
        String message = severity.equals("NORMAL")
                ? DATE_FORMAT.format(deadline) + " 판매 종료일까지 " + disposal.nearestSaleEndDays() + "일 남았고 30일 예상 폐기수량은 "
                + quantity(disposal.quantity()) + "개(" + rate(disposal.rate()) + "%)로 즉시 조치 기준 미만이지만 추이를 관찰해야 합니다."
                : DATE_FORMAT.format(deadline) + " 판매 종료일까지 " + disposal.nearestSaleEndDays() + "일 남았으며 30일 예상 폐기수량은 "
                + quantity(disposal.quantity()) + "개로, 현재 판매 가능 재고 " + quantity(available) + "개의 " + rate(disposal.rate()) + "%입니다.";
        reasons.add(reason(code, message, severity, "rawRate=" + disposal.rawRate() + ", maxDeadline=" + deadline));
    }

    private static void addMediumTermReasons(List<RiskReason> reasons, ExpectedDisposal disposal, LocalDate date, BigDecimal available) {
        if (disposal.mediumTermQuantity90().signum() <= 0 || disposal.mediumTermSaleEndDays() == null) return;
        boolean caution = disposal.mediumTermRate90Raw().compareTo(DANGER_RATE) >= 0;
        String code = caution ? "MEDIUM_TERM_DISPOSAL_CAUTION" : "MEDIUM_TERM_DISPOSAL_MONITORING";
        String severity = caution ? "WARNING" : "NORMAL";
        LocalDate end = date.plusDays(disposal.mediumTermSaleEndDays());
        String message = "현재 30일 예상 폐기수량은 " + quantity(disposal.quantity()) + "개입니다. 다만 "
                + DATE_FORMAT.format(end) + "(" + disposal.mediumTermSaleEndDays() + "일 후) 판매 종료되는 LOT에서 "
                + quantity(disposal.mediumTermQuantity90()) + "개(" + rate(disposal.mediumTermRate90Raw()) + "%)가 남을 것으로 예상"
                + (caution ? "됩니다." : "되어 중기 재고 추이를 관찰해야 합니다.");
        reasons.add(reason(code, message, severity, "rawRate=" + disposal.mediumTermRate90Raw() + ", availableQty=" + available));
    }

    private static void addLongTermReasons(List<RiskReason> reasons, BigDecimal projectedD60, BigDecimal overstock60,
                                           BigDecimal overstock90, BigDecimal rate90, BigDecimal available, BigDecimal safety) {
        if (overstock90 != null && overstock90.signum() > 0) {
            boolean caution = rate90.compareTo(DANGER_RATE) >= 0;
            String code = caution ? "LONG_TERM_OVERSTOCK_CAUTION" : "LONG_TERM_OVERSTOCK_MONITORING";
            String severity = caution ? "WARNING" : "NORMAL";
            String message = caution ? "현재 30일 즉시 위험은 없습니다. 다만 90일 후 안전재고를 제외한 "
                    + quantity(overstock90) + "개(" + rate(rate90) + "%)가 남을 것으로 예상되어 장기 과잉재고 관리가 필요합니다."
                    : "현재 30일 즉시 위험은 없습니다. 다만 90일 후 안전재고를 제외한 "
                    + quantity(overstock90) + "개(" + rate(rate90) + "%)가 남을 것으로 예상되어 장기 재고 추이를 관찰해야 합니다.";
            reasons.add(reason(code, message, severity, "projectedD60=" + projectedD60 + ", availableQty=" + available + ", safetyStock=" + safety));
        } else if (overstock60 != null && overstock60.signum() > 0) {
            reasons.add(reason("LONG_TERM_CLEARING_MONITORING",
                    "현재 30일 기준 위험은 없습니다. 60일 후 안전재고를 제외한 " + quantity(overstock60)
                            + "개가 남지만 90일 이내 안전재고 수준까지 소진될 것으로 예상됩니다.", "NORMAL",
                    "overstockQty60=" + overstock60));
        }
    }

    private static ExpectedDisposal calculateDisposal(List<LotView> lots, RiskAssessmentInput input, LocalDate date,
                                                      BigDecimal available, boolean forecastUsable) {
        Map<LocalDate, BigDecimal> byDeadline = new TreeMap<>();
        LocalDate horizon = date.plusDays(LONG_HORIZON_DAYS);
        for (LotView lot : lots) {
            if (lot.status() != LotStatus.AVAILABLE || lot.onHandQty().signum() <= 0) continue;
            LocalDate end = effectiveSaleEndDate(lot.item());
            if (end == null || !end.isAfter(date) || end.isAfter(horizon)) continue;
            byDeadline.merge(end, lot.onHandQty(), BigDecimal::add);
        }
        if (byDeadline.isEmpty()) return ExpectedDisposal.empty();

        BigDecimal cumulative = ZERO, max30 = ZERO, max90 = ZERO, disposal60 = ZERO, disposal90 = ZERO;
        LocalDate max30Date = null, max90Date = null;
        for (Map.Entry<LocalDate, BigDecimal> entry : byDeadline.entrySet()) {
            int days = (int) ChronoUnit.DAYS.between(date, entry.getKey());
            cumulative = cumulative.add(entry.getValue());
            BigDecimal demand = forecastUsable ? demandAtDay(input, days) : ZERO;
            BigDecimal raw = cumulative.subtract(demand).max(ZERO);
            if (days <= DISPOSAL_HORIZON_DAYS && raw.compareTo(max30) > 0) { max30 = raw; max30Date = entry.getKey(); }
            if (raw.compareTo(max90) > 0) { max90 = raw; max90Date = entry.getKey(); }
            if (days <= 60) disposal60 = disposal60.max(raw);
            disposal90 = disposal90.max(raw);
        }
        BigDecimal medium = max90.subtract(max30).max(ZERO);
        LocalDate mediumDate = firstDateWithDelta(byDeadline, date, input, forecastUsable, max30, medium);
        if (medium.signum() == 0) mediumDate = null;
        LocalDate primaryDate = max30.signum() > 0 ? max30Date : byDeadline.keySet().stream()
                .filter(end -> !end.isAfter(date.plusDays(DISPOSAL_HORIZON_DAYS))).findFirst().orElse(null);
        Integer nearest = primaryDate == null ? null : (int) ChronoUnit.DAYS.between(date, primaryDate);
        Integer mediumDays = mediumDate == null ? null : (int) ChronoUnit.DAYS.between(date, mediumDate);
        BigDecimal rate = percentage(max30, available), mediumRate = percentage(medium, available);
        return new ExpectedDisposal(max30, rate, rawPercentage(max30, available),
                nearest, max90, medium, mediumRate, rawPercentage(medium, available),
                mediumDays, disposal60, disposal90, max30Date, max90Date, mediumDate);
    }

    private static LocalDate firstDateWithDelta(Map<LocalDate, BigDecimal> byDeadline, LocalDate date, RiskAssessmentInput input,
                                                boolean forecastUsable, BigDecimal e30, BigDecimal target) {
        if (target.signum() == 0) return null;
        BigDecimal cumulative = ZERO;
        for (Map.Entry<LocalDate, BigDecimal> entry : byDeadline.entrySet()) {
            int days = (int) ChronoUnit.DAYS.between(date, entry.getKey());
            cumulative = cumulative.add(entry.getValue());
            BigDecimal demand = forecastUsable ? demandAtDay(input, days) : ZERO;
            BigDecimal raw = cumulative.subtract(demand).max(ZERO);
            if (days > DISPOSAL_HORIZON_DAYS && raw.subtract(e30).max(ZERO).compareTo(target) >= 0) return entry.getKey();
        }
        return null;
    }

    private static BigDecimal predictedQtyAtDay(RiskAssessmentInput input, int days) {
        if (days <= 0) return ZERO;
        if (days <= 7) return interpolate(ZERO, input.predictedQtyD7(), days, 7);
        if (days <= 14) return interpolate(input.predictedQtyD7(), input.predictedQtyD14(), days - 7, 7);
        if (days <= 30) return interpolate(input.predictedQtyD14(), input.predictedQtyD30(), days - 14, 16);
        if (days <= 60) return interpolate(input.predictedQtyD30(), input.predictedQtyD60(), days - 30, 30);
        return interpolate(input.predictedQtyD60(), input.predictedQtyD90(), days - 60, 30);
    }

    private static BigDecimal demandAtDay(RiskAssessmentInput input, int days) {
        if (days > DISPOSAL_HORIZON_DAYS && !input.extendedForecastProvided()) return ZERO;
        return predictedQtyAtDay(input, days);
    }

    private static BigDecimal interpolate(BigDecimal start, BigDecimal end, int elapsed, int span) {
        return start.add(end.subtract(start).multiply(BigDecimal.valueOf(elapsed)).divide(BigDecimal.valueOf(span), 12, RoundingMode.HALF_UP));
    }

    private static LocalDate effectiveSaleEndDate(RiskAssessmentInput.LotRiskItem lot) {
        if (lot.expiryDate() == null) return lot.saleStopDate();
        if (lot.saleStopDate() == null) return lot.expiryDate();
        return lot.expiryDate().isBefore(lot.saleStopDate()) ? lot.expiryDate() : lot.saleStopDate();
    }

    private static BigDecimal remainingSellable(BigDecimal available, BigDecimal demand, BigDecimal disposal) {
        return available.subtract(demand).subtract(disposal).max(ZERO);
    }

    private static BigDecimal calculateSafetyGap(BigDecimal available, BigDecimal projected, BigDecimal safety) {
        if (safety == null) return null;
        if (available.compareTo(safety) < 0) return safety.subtract(available);
        if (projected != null && projected.compareTo(safety) < 0) return safety.subtract(projected);
        return ZERO;
    }

    private static BigDecimal stockCoverageDays(BigDecimal available, BigDecimal demand) {
        if (demand.signum() <= 0) return BigDecimal.valueOf(Long.MAX_VALUE);
        return available.multiply(BigDecimal.valueOf(30)).divide(demand, 12, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) return ZERO.setScale(2);
        return numerator.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rawPercentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) return ZERO;
        return numerator.multiply(HUNDRED).divide(denominator, 12, RoundingMode.HALF_UP);
    }

    private static Integer nearestExpiryDays(List<LotView> lots, LocalDate date) {
        return lots.stream().filter(lot -> lot.status() == LotStatus.AVAILABLE && lot.onHandQty().signum() > 0)
                .map(lot -> lot.item().expiryDate()).filter(Objects::nonNull)
                .mapToInt(end -> (int) ChronoUnit.DAYS.between(date, end)).boxed().min(Integer::compareTo).orElse(null);
    }

    private static Integer maxHoldingDays(List<LotView> lots, LocalDate date) {
        return lots.stream().filter(lot -> lot.status() == LotStatus.AVAILABLE && lot.onHandQty().signum() > 0)
                .map(lot -> lot.item().receivedDate()).filter(Objects::nonNull)
                .mapToInt(received -> (int) ChronoUnit.DAYS.between(received, date)).boxed().max(Integer::compareTo).orElse(null);
    }

    private static RiskReason reason(String code, String message, String severity, String evidence) {
        return new RiskReason(code, message, severity, evidence);
    }

    private static int compareReasons(RiskReason left, RiskReason right) {
        int severity = Integer.compare(severityRank(right.severity()), severityRank(left.severity()));
        if (severity != 0) return severity;
        int priority = Integer.compare(priority(left.code()), priority(right.code()));
        if (priority != 0) return priority;
        int code = nullSafe(left.code()).compareTo(nullSafe(right.code()));
        return code != 0 ? code : nullSafe(left.message()).compareTo(nullSafe(right.message()));
    }

    private static int priority(String code) { return REASON_PRIORITY.getOrDefault(code, Integer.MAX_VALUE); }

    private static int severityRank(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "WARNING" -> 3;
            case "NORMAL" -> 2;
            case "GOOD" -> 1;
            default -> 0;
        };
    }

    private static String toDbGrade(String severity) {
        return switch (severityRank(severity)) {
            case 4 -> "CRITICAL";
            case 3 -> "WARNING";
            case 2 -> "NORMAL";
            default -> "GOOD";
        };
    }

    private static String warehouseClearMessage(BigDecimal available, BigDecimal excluded, BigDecimal disposal) {
        return "현재 판매 가능 재고는 " + quantity(available) + "개이며, 판매 불가 재고와 30일 이내 판매 종료 예정 재고는 "
                + quantity(excluded.add(disposal)) + "개입니다.";
    }

    private static String limitedBasisMessage(BigDecimal available, BigDecimal excluded) {
        String excludedMessage = excluded.signum() > 0
                ? ", 판매 불가 LOT 재고 " + quantity(excluded) + "개를 제외했습니다. "
                : ", ";
        return "현재 판매 가능 재고는 " + quantity(available) + "개이며" + excludedMessage
                + "현재 확인 가능한 기준에서 보통으로 판정했습니다.";
    }

    private static String currentPolicyClearMessage(BigDecimal available, BigDecimal safety, BigDecimal excluded) {
        String message = "현재 판매 가능 재고 " + quantity(available) + "개가 안전재고 " + quantity(safety) + "개를 충족합니다";
        if (excluded.signum() > 0) message += ". 판매 불가 LOT의 재고 " + quantity(excluded) + "개를 제외했습니다";
        return message + ".";
    }

    private static String optimalStockMessage(BigDecimal available, BigDecimal f30, BigDecimal safety) {
        String basis = safety == null ? "30일 예측수요 " + quantity(f30) + "개를 충족하며" : "안전재고 " + quantity(safety) + "개를 충족하고";
        return "현재 판매 가능 재고 " + quantity(available) + "개가 " + basis
                + ", 30일 예상 폐기수량은 0개이며 90일 이내 장기 과잉재고도 예상되지 않습니다.";
    }

    private static String quantity(BigDecimal value) { return (value == null ? ZERO : value).setScale(0, RoundingMode.HALF_UP).toPlainString(); }
    private static String rate(BigDecimal value) { return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static String decimal(BigDecimal value) { return rate(value); }
    private static BigDecimal nullableZero(BigDecimal value) { return value == null ? ZERO : value; }
    private static String nullSafe(String value) { return value == null ? "" : value; }

    private enum LotStatus { EXPIRED, SALE_STOPPED, DEPLETED, AVAILABLE }

    private record LotView(RiskAssessmentInput.LotRiskItem item, LotStatus status, BigDecimal onHandQty,
                           BigDecimal reservedQty, BigDecimal integratedBalance) {
        private String sortKey() { return lotKey(item) + "|" + status + "|" + onHandQty + "|" + reservedQty; }
    }

    private record ExpectedDisposal(BigDecimal quantity, BigDecimal rate, BigDecimal rawRate, Integer nearestSaleEndDays,
                                    BigDecimal quantity90, BigDecimal mediumTermQuantity90, BigDecimal mediumTermRate90,
                                    BigDecimal mediumTermRate90Raw, Integer mediumTermSaleEndDays, BigDecimal disposalAt60,
                                    BigDecimal disposalAt90, LocalDate max30Date, LocalDate max90Date, LocalDate mediumTermDate) {
        private static ExpectedDisposal empty() {
            BigDecimal zero = ZERO.setScale(2);
            return new ExpectedDisposal(ZERO, zero, ZERO, null, ZERO, ZERO, zero, ZERO, null, ZERO, ZERO, null, null, null);
        }
    }
}
