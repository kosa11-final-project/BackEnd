package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

@Component
public class RiskRuleEngine {

    // 수요예측·가용재고·안전재고·LOT만 사용하는 서버 규칙 버전입니다.
    public static final String RULE_VERSION = "v1.6.0";
    private static final BigDecimal CAUTION_STOCK_DAYS = BigDecimal.valueOf(14);
    private static final int EXPECTED_DISPOSAL_HORIZON_DAYS = 30;
    private static final int URGENT_SALE_END_DAYS = 7;
    private static final BigDecimal CAUTION_DISPOSAL_RATE = BigDecimal.valueOf(5);
    private static final BigDecimal DANGER_DISPOSAL_RATE = BigDecimal.valueOf(20);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public RiskRuleEngine() {
        this(Clock.system(BUSINESS_ZONE));
    }

    public RiskRuleEngine(Clock clock) {
        this.clock = clock;
    }

    public RiskAssessmentResult evaluate(RiskAssessmentInput input) {
        Instant now = Instant.now(clock);
        LocalDate baseDate = input.baseDate() != null ? input.baseDate() : LocalDate.now(clock);
        LocalDate assessmentDate = input.assessmentDate() != null ? input.assessmentDate() : baseDate;

        // 1. 입력 검증. 음수 수량은 잘못된 입력이므로 판정하지 않고 동기화를 차단합니다.
        if (input.onHandQty() != null && input.onHandQty().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("on_hand_qty must be non-negative");
        }
        if (input.safetyStockQty() != null && input.safetyStockQty().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("safetyStockQty must be non-negative");
        }
        if (input.lots() != null && input.lots().stream()
                .anyMatch(lot -> lot.quantity() != null && lot.quantity().compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("lot quantity must be non-negative");
        }

        boolean inventoryMissing = input.onHandQty() == null;
        BigDecimal physicalAvailableQty = inventoryMissing ? BigDecimal.ZERO : input.onHandQty();
        BigDecimal excludedLotQty = BigDecimal.ZERO;
        List<RiskReason> excludedLotReasons = new ArrayList<>();

        // 이미 판매중지·소비기한 경과·소진된 LOT는 물리 재고로는 남아 있어도 판매할 수 없습니다.
        // 해당 수량은 재고 부족·수요예측·안전재고 판정에서 제외하고 운영 조치 정보로만 남깁니다.
        if (input.lots() != null) {
            for (RiskAssessmentInput.LotRiskItem lot : input.lots()) {
                boolean hasQuantity = lot.quantity() != null && lot.quantity().compareTo(BigDecimal.ZERO) > 0;
                if (!hasQuantity) {
                    continue;
                }

                boolean expired = isExpired(lot, assessmentDate);
                boolean saleStopped = isSaleStopped(lot, assessmentDate);
                boolean depleted = isDepleted(lot);
                if (expired || saleStopped || depleted) {
                    excludedLotQty = excludedLotQty.add(lot.quantity());
                }
                if (expired) {
                    excludedLotReasons.add(new RiskReason(
                            "LOT_EXPIRED_EXCLUDED",
                            "소비기한 경과 LOT를 판매 가능 재고에서 제외했습니다 (" + lot.lotNumber() + ")",
                            "INFO",
                            "expiryDate=" + lot.expiryDate() + ", qty=" + lot.quantity()
                    ));
                }
                if (saleStopped) {
                    excludedLotReasons.add(new RiskReason(
                            "LOT_SALE_STOPPED_EXCLUDED",
                            "판매중지 LOT를 판매 가능 재고에서 제외했습니다 (" + lot.lotNumber() + ")",
                            "INFO",
                            "saleStopDate=" + lot.saleStopDate() + ", qty=" + lot.quantity()
                    ));
                }
            }
        }

        BigDecimal availableQty = physicalAvailableQty.subtract(excludedLotQty).max(BigDecimal.ZERO);
        boolean forecastUsable = isUsableForecast(input);

        // 2. 수요예측이 유효할 때만 예측 기반 규칙과 수치를 계산합니다.
        // 기준일이 오래된 forecast도 값 자체가 유효하면 그대로 사용합니다.
        BigDecimal predictedQtyD7 = forecastUsable ? input.predictedQtyD7() : null;
        BigDecimal projectedD7 = predictedQtyD7 == null
                ? null
                : availableQty.subtract(predictedQtyD7).max(BigDecimal.ZERO);

        BigDecimal predictedQtyD30 = forecastUsable ? input.predictedQtyD30() : null;
        BigDecimal shortageQty30 = null;
        if (predictedQtyD30 != null && predictedQtyD30.compareTo(availableQty) > 0) {
            shortageQty30 = predictedQtyD30.subtract(availableQty);
        } else if (predictedQtyD30 != null) {
            shortageQty30 = BigDecimal.ZERO;
        }

        BigDecimal safetyStock = input.safetyStockQty();
        BigDecimal safetyGap = null;
        if (safetyStock != null && projectedD7 != null && safetyStock.compareTo(projectedD7) > 0) {
            safetyGap = safetyStock.subtract(projectedD7);
        }

        ExpectedDisposal expectedDisposal = calculateExpectedDisposal(
                input,
                assessmentDate,
                availableQty,
                forecastUsable
        );

        // 3. LOT 기반 소비기한 및 보유일수 계산
        Integer nearestExpiryDays = null;
        Integer maxHoldingDays = null;
        List<RiskReason> reasons = new ArrayList<>();

        // 예측이 없거나 사용할 수 없는 경우에도 현재 재고·안전재고·LOT 서버 룰은 적용합니다.
        // 수요예측 부재 자체는 위험등급을 올리는 사유가 아닙니다. 현재 재고와 안전재고,
        // LOT 상태가 양호하면 SAFE로 판정할 수 있도록 하되, 안전재고 정책이 없는 경우에는
        // 미래 수요를 확인할 기준이 부족하므로 NORMAL을 유지합니다.
        if (!forecastUsable) {
            boolean forecastMissing = !input.forecastAvailable();
            reasons.add(new RiskReason(
                    forecastMissing ? "FORECAST_UNAVAILABLE" : "FORECAST_INVALID",
                    forecastMissing
                            ? "수요예측을 확인할 수 없는 상황입니다."
                            : "수요예측 값이 유효하지 않은 상황입니다.",
                    "INFO",
                    forecastMissing ? "forecastAvailable=false" : "forecast values are invalid"
            ));
        }

        if (inventoryMissing) {
            reasons.add(new RiskReason(
                    "DATA_MISSING",
                    "현재 가용재고 정보를 확인할 수 없어 부족 위험이 높은 상황입니다.",
                    "CRITICAL",
                    "on_hand_qty is null"
            ));
        } else if (availableQty.signum() == 0) {
            reasons.add(new RiskReason(
                    "ZERO_AVAILABLE_STOCK",
                    "현재 판매 가능한 가용재고가 없어 부족한 상황입니다.",
                    "CRITICAL",
                    "physicalAvailableQty=" + physicalAvailableQty + ", excludedLotQty=" + excludedLotQty
            ));
        }

        if (input.lots() != null && !input.lots().isEmpty()) {
            for (RiskAssessmentInput.LotRiskItem lot : input.lots()) {
                boolean hasQuantity = lot.quantity() != null && lot.quantity().compareTo(BigDecimal.ZERO) > 0;
                boolean sellable = hasQuantity && isSellable(lot, assessmentDate);
                if (sellable && lot.expiryDate() != null) {
                    long days = ChronoUnit.DAYS.between(assessmentDate, lot.expiryDate());
                    int daysInt = (int) days;
                    if (nearestExpiryDays == null || daysInt < nearestExpiryDays) {
                        nearestExpiryDays = daysInt;
                    }
                }

                if (sellable && lot.receivedDate() != null) {
                    long holding = ChronoUnit.DAYS.between(lot.receivedDate(), assessmentDate);
                    int holdingInt = (int) holding;
                    if (maxHoldingDays == null || holdingInt > maxHoldingDays) {
                        maxHoldingDays = holdingInt;
                    }
                }
            }
        }

        if (!forecastUsable && input.safetyStockQty() == null) {
            reasons.add(new RiskReason(
                    "FORECAST_WITHOUT_SAFETY_POLICY",
                    "수요예측과 안전재고 기준이 없어 재고 상태를 확정하기 어려운 상황입니다.",
                    "NORMAL",
                    "safetyStockQty=null"
            ));
        }

        // 4. 규칙 기반 위험 사유 평가
        // A. 향후 30일 판매종료 LOT의 실제 종료일까지 판매하고 남을 수량과 비율을 함께 평가합니다.
        // 날짜만 30일 이내라는 이유로 위험을 올리지 않고, 현재 수요예측으로 전량 소진 가능한 경우에는
        // 폐기 위험을 올리지 않습니다. 5%와 20%는 법정 기준이 아닌 초기 운영 임계값입니다.
        if (expectedDisposal.quantity().signum() > 0) {
            boolean urgent = expectedDisposal.nearestSaleEndDays() != null
                    && expectedDisposal.nearestSaleEndDays() <= URGENT_SALE_END_DAYS;
            boolean danger = expectedDisposal.rate().compareTo(DANGER_DISPOSAL_RATE) >= 0
                    || (urgent && expectedDisposal.rate().compareTo(CAUTION_DISPOSAL_RATE) >= 0);
            boolean caution = expectedDisposal.rate().compareTo(CAUTION_DISPOSAL_RATE) >= 0 || urgent;
            String severity = danger ? "CRITICAL" : caution ? "WARNING" : "NORMAL";
            String code = danger
                    ? "EXPECTED_DISPOSAL_DANGER"
                    : caution ? "EXPECTED_DISPOSAL_CAUTION" : "EXPECTED_DISPOSAL_MONITORING";
            reasons.add(new RiskReason(
                    code,
                    "판매 종료까지 " + expectedDisposal.nearestSaleEndDays() + "일 남았고, 30일 예상 폐기수량은 "
                            + expectedDisposal.quantity().stripTrailingZeros().toPlainString()
                            + "개(현재 판매 가능 재고의 "
                            + expectedDisposal.rate().setScale(2, RoundingMode.HALF_UP).toPlainString()
                            + "%)인 상황입니다.",
                    severity,
                    "nearestSaleEndDays=" + expectedDisposal.nearestSaleEndDays()
                            + ", expectedDisposalQty30=" + expectedDisposal.quantity()
                            + ", expectedDisposalRate30=" + expectedDisposal.rate()
            ));
        } else if (expectedDisposal.nearestSaleEndDays() != null) {
            reasons.add(new RiskReason(
                    "EXPECTED_DISPOSAL_CLEAR",
                    "30일 안에 판매가 종료되는 LOT가 있지만 현재 수요예측으로 기한 내 소진 가능한 상황입니다.",
                    "INFO",
                    "nearestSaleEndDays=" + expectedDisposal.nearestSaleEndDays()
                            + ", expectedDisposalQty30=0"
            ));
        }

        // B. 수요예측 기반 부족량 평가
        if (shortageQty30 != null && shortageQty30.compareTo(BigDecimal.ZERO) > 0) {
            boolean cautionShortage = availableQty.multiply(BigDecimal.valueOf(30))
                    .compareTo(predictedQtyD30.multiply(CAUTION_STOCK_DAYS)) < 0;
            reasons.add(new RiskReason(
                    cautionShortage ? "PREDICTED_SHORTAGE" : "PREDICTED_SHORTAGE_MONITORING",
                    cautionShortage
                            ? "D+30 수요예측 기준으로 재고 부족이 예상되는 상황입니다. (" + shortageQty30 + "개 부족 예상)"
                            : "D+30 수요예측 기준으로 부족 가능성이 있어 보충 검토가 필요한 상황입니다. (" + shortageQty30 + "개 부족 예상)",
                    cautionShortage ? "WARNING" : "NORMAL",
                    "predictedQtyD30=" + predictedQtyD30 + ", availableQty=" + availableQty
            ));
        }

        // C. 안전재고 미달 평가
        if (safetyStock != null && safetyStock.compareTo(BigDecimal.ZERO) > 0) {
            if (projectedD7 != null && projectedD7.compareTo(safetyStock) < 0) {
                reasons.add(new RiskReason(
                        "PROJECTED_UNDER_SAFETY",
                        "7일 후 예상 재고(" + projectedD7 + "개)가 안전재고(" + safetyStock + "개)보다 적어 부족이 예상되는 상황입니다.",
                        "CRITICAL",
                        "projectedD7=" + projectedD7 + ", safetyStock=" + safetyStock
                ));
            }
            if (availableQty.compareTo(safetyStock) < 0) {
                reasons.add(new RiskReason(
                        "CURRENT_UNDER_SAFETY",
                        "현재 가용재고(" + availableQty + "개)가 안전재고(" + safetyStock + "개)보다 적어 부족한 상황입니다.",
                        "WARNING",
                        "availableQty=" + availableQty + ", safetyStock=" + safetyStock
                ));
            }
        }

        // 5. 등급 결정: CRITICAL > WARNING > NORMAL > GOOD
        String dbGrade = "GOOD";
        String apiGrade = "SAFE";

        boolean hasCritical = reasons.stream().anyMatch(r -> "CRITICAL".equalsIgnoreCase(r.severity()));
        boolean hasWarning = reasons.stream().anyMatch(r -> "WARNING".equalsIgnoreCase(r.severity()));
        boolean hasNormal = reasons.stream().anyMatch(r -> "NORMAL".equalsIgnoreCase(r.severity()));

        if (hasCritical) {
            dbGrade = "CRITICAL";
            apiGrade = "DANGER";
        } else if (hasWarning) {
            dbGrade = "WARNING";
            apiGrade = "CAUTION";
        } else if (hasNormal) {
            dbGrade = "NORMAL";
            apiGrade = "NORMAL";
        } else {
            dbGrade = "GOOD";
            apiGrade = "SAFE";
            reasons.add(new RiskReason(
                    "OPTIMAL_STOCK",
                    "현재 가용재고와 LOT 상태가 양호해 안정적인 재고 상태입니다.",
                    "GOOD",
                    "availableQty=" + availableQty
            ));
        }

        reasons.addAll(excludedLotReasons);
        reasons.sort((left, right) -> Integer.compare(
                severityRank(right.severity()),
                severityRank(left.severity())
        ));

        RiskReason primaryRiskReason = reasons.get(0);
        String primaryReason = primaryRiskReason.message();
        String forecastNote = forecastNote(input, forecastUsable);
        if (forecastNote != null && !"FORECAST_WITHOUT_SAFETY_POLICY".equals(primaryRiskReason.code())) {
            primaryReason += " " + forecastNote;
        }
        if (safetyStock == null && !"FORECAST_WITHOUT_SAFETY_POLICY".equals(primaryRiskReason.code())) {
            primaryReason += " 안전재고 기준이 없어 부족 여부를 확정하기 어려운 상황입니다.";
        }
        return new RiskAssessmentResult(
                "ASSESSED",
                dbGrade,
                apiGrade,
                primaryReason,
                reasons,
                availableQty,
                shortageQty30,
                safetyGap,
                projectedD7,
                safetyStock,
                expectedDisposal.quantity(),
                expectedDisposal.rate(),
                expectedDisposal.nearestSaleEndDays(),
                nearestExpiryDays,
                maxHoldingDays,
                baseDate,
                now,
                RULE_VERSION
        );
    }

    private static int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "WARNING" -> 3;
            case "NORMAL" -> 2;
            case "GOOD" -> 1;
            default -> 0;
        };
    }

    private static boolean isUsableForecast(RiskAssessmentInput input) {
        if (!input.forecastAvailable()
                || input.predictedQtyD7() == null
                || input.predictedQtyD14() == null
                || input.predictedQtyD30() == null) {
            return false;
        }
        return input.predictedQtyD7().compareTo(BigDecimal.ZERO) >= 0
                && input.predictedQtyD14().compareTo(input.predictedQtyD7()) >= 0
                && input.predictedQtyD30().compareTo(input.predictedQtyD14()) >= 0;
    }

    private static ExpectedDisposal calculateExpectedDisposal(
            RiskAssessmentInput input,
            LocalDate assessmentDate,
            BigDecimal availableQty,
            boolean forecastUsable
    ) {
        Map<LocalDate, BigDecimal> quantityBySaleEndDate = new TreeMap<>();
        LocalDate horizonEnd = assessmentDate.plusDays(EXPECTED_DISPOSAL_HORIZON_DAYS);

        if (input.lots() != null) {
            for (RiskAssessmentInput.LotRiskItem lot : input.lots()) {
                boolean hasQuantity = lot.quantity() != null && lot.quantity().signum() > 0;
                if (!hasQuantity || isDepleted(lot)) {
                    continue;
                }
                LocalDate saleEndDate = effectiveSaleEndDate(lot);
                if (saleEndDate == null
                        || !saleEndDate.isAfter(assessmentDate)
                        || saleEndDate.isAfter(horizonEnd)) {
                    continue;
                }
                quantityBySaleEndDate.merge(saleEndDate, lot.quantity(), BigDecimal::add);
            }
        }

        if (quantityBySaleEndDate.isEmpty()) {
            return new ExpectedDisposal(BigDecimal.ZERO, BigDecimal.ZERO, null);
        }

        boolean useForecast = forecastUsable && !"UNASSIGNED".equalsIgnoreCase(input.salesPointCode());
        BigDecimal cumulativeEndingQty = BigDecimal.ZERO;
        BigDecimal expectedDisposalQty = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> deadline : quantityBySaleEndDate.entrySet()) {
            cumulativeEndingQty = cumulativeEndingQty.add(deadline.getValue());
            int saleEndDays = (int) ChronoUnit.DAYS.between(assessmentDate, deadline.getKey());
            BigDecimal predictedQtyAtSaleEnd = useForecast
                    ? predictedQtyAtDay(input, saleEndDays)
                    : BigDecimal.ZERO;
            expectedDisposalQty = expectedDisposalQty.max(
                    cumulativeEndingQty.subtract(predictedQtyAtSaleEnd).max(BigDecimal.ZERO)
            );
        }

        BigDecimal expectedDisposalRate = availableQty == null || availableQty.signum() <= 0
                ? BigDecimal.ZERO
                : expectedDisposalQty.multiply(BigDecimal.valueOf(100))
                        .divide(availableQty, 2, RoundingMode.HALF_UP);
        int nearestSaleEndDays = (int) ChronoUnit.DAYS.between(
                assessmentDate,
                quantityBySaleEndDate.keySet().iterator().next()
        );
        return new ExpectedDisposal(expectedDisposalQty, expectedDisposalRate, nearestSaleEndDays);
    }

    private static BigDecimal predictedQtyAtDay(RiskAssessmentInput input, int days) {
        if (days <= 7) {
            return input.predictedQtyD7()
                    .multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP);
        }
        if (days <= 14) {
            return input.predictedQtyD7().add(
                    input.predictedQtyD14().subtract(input.predictedQtyD7())
                            .multiply(BigDecimal.valueOf(days - 7))
                            .divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP)
            );
        }
        return input.predictedQtyD14().add(
                input.predictedQtyD30().subtract(input.predictedQtyD14())
                        .multiply(BigDecimal.valueOf(days - 14))
                        .divide(BigDecimal.valueOf(16), 6, RoundingMode.HALF_UP)
        );
    }

    private static LocalDate effectiveSaleEndDate(RiskAssessmentInput.LotRiskItem lot) {
        if (lot.expiryDate() == null) {
            return lot.saleStopDate();
        }
        if (lot.saleStopDate() == null) {
            return lot.expiryDate();
        }
        return lot.expiryDate().isBefore(lot.saleStopDate()) ? lot.expiryDate() : lot.saleStopDate();
    }

    private static String forecastNote(RiskAssessmentInput input, boolean forecastUsable) {
        if (forecastUsable && input.forecastStale()) {
            return "수요예측 기준일이 오래되어 현재 확보된 값으로 확인한 상황입니다.";
        }
        if (!forecastUsable && !input.forecastAvailable()) {
            return "수요예측을 확인할 수 없어 현재 재고 기준으로 확인한 상황입니다.";
        }
        if (!forecastUsable) {
            return "수요예측 값이 유효하지 않아 현재 재고 기준으로 확인한 상황입니다.";
        }
        return null;
    }

    private static boolean isExpired(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return lot.expiryDate() != null && !lot.expiryDate().isAfter(baseDate);
    }

    private static boolean isSaleStopped(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return lot.saleStopDate() != null && !lot.saleStopDate().isAfter(baseDate);
    }

    private static boolean isDepleted(RiskAssessmentInput.LotRiskItem lot) {
        return "DEPLETED".equalsIgnoreCase(lot.lotStatus());
    }

    private static boolean isSellable(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return !isExpired(lot, baseDate) && !isSaleStopped(lot, baseDate) && !isDepleted(lot);
    }

    private record ExpectedDisposal(
            BigDecimal quantity,
            BigDecimal rate,
            Integer nearestSaleEndDays
    ) {
    }

}
