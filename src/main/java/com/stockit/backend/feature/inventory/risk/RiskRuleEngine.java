package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class RiskRuleEngine {

    // 수요예측·가용재고·안전재고·LOT만 사용하는 서버 규칙 버전입니다.
    public static final String RULE_VERSION = "v1.1.0";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public RiskRuleEngine() {
        this(Clock.system(BUSINESS_ZONE));
    }

    RiskRuleEngine(Clock clock) {
        this.clock = clock;
    }

    public RiskAssessmentResult evaluate(RiskAssessmentInput input) {
        Instant now = Instant.now(clock);
        LocalDate baseDate = input.baseDate() != null ? input.baseDate() : LocalDate.now(clock);

        // 1. 필수 입력 검증. 위험등급은 재고·수요예측·정책·LOT 데이터로 판정합니다.
        if (input.onHandQty() == null) {
            return new RiskAssessmentResult(
                    "UNASSESSED",
                    null,
                    "UNASSESSED",
                    "가용수량이 누락되어 위험등급을 판정할 수 없습니다.",
                    List.of(new RiskReason("DATA_MISSING", "가용수량 정보 없음", "WARNING", "onHandQty is null")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    baseDate,
                    now,
                    RULE_VERSION
            );
        }

        if (input.onHandQty().compareTo(BigDecimal.ZERO) < 0) {
            return unassessed(
                    "INVALID_INVENTORY_DATA",
                    "가용수량이 음수여서 위험등급을 판정할 수 없습니다.",
                    "on_hand_qty must be non-negative",
                    baseDate,
                    now
            );
        }

        if (input.forecastStale()) {
            return stale(
                    "STALE_FORECAST",
                    "수요예측 기준일이 오래되어 위험등급을 최신 상태로 판정할 수 없습니다.",
                    "forecast base date is older than 14 days",
                    baseDate,
                    now
            );
        }

        if (!input.forecastAvailable()) {
            return unassessed(
                    "MISSING_FORECAST",
                    "수요예측 결과가 없어 위험등급을 판정할 수 없습니다.",
                    "forecast horizon is missing",
                    baseDate,
                    now
            );
        }

        if (input.predictedQtyD7() == null
                || input.predictedQtyD30() == null
                || input.predictedQtyD7().compareTo(BigDecimal.ZERO) < 0
                || input.predictedQtyD30().compareTo(input.predictedQtyD7()) < 0) {
            return unassessed(
                    "INVALID_FORECAST_DATA",
                    "수요예측 누적값이 음수이거나 기간 순서를 만족하지 않아 위험등급을 판정할 수 없습니다.",
                    "forecast horizons are invalid",
                    baseDate,
                    now
            );
        }

        if (input.safetyStockQty() == null) {
            return unassessed(
                    "MISSING_POLICY",
                    "안전재고 정책이 없어 위험등급을 판정할 수 없습니다.",
                    "active inventory policy is missing",
                    baseDate,
                    now
            );
        }

        if (input.safetyStockQty().compareTo(BigDecimal.ZERO) < 0) {
            return unassessed(
                    "INVALID_POLICY_DATA",
                    "안전재고 기준이 음수여서 위험등급을 판정할 수 없습니다.",
                    "safetyStockQty must be non-negative",
                    baseDate,
                    now
            );
        }

        if (input.lots() != null && input.lots().stream()
                .anyMatch(lot -> lot.quantity() != null && lot.quantity().compareTo(BigDecimal.ZERO) < 0)) {
            return unassessed(
                    "INVALID_INVENTORY_DATA",
                    "LOT 수량이 음수여서 위험등급을 판정할 수 없습니다.",
                    "lot quantity must be non-negative",
                    baseDate,
                    now
            );
        }

        // 현재 canonical 계약에서는 on_hand_qty 자체가 예약을 제외한 가용수량입니다.
        // 소비기한·판매중지에 따른 별도 차감 컬럼은 후속 스키마 결정에서 반영합니다.
        BigDecimal availableQty = input.onHandQty();

        // 2. 수요예측·안전재고 기준으로 예상 잔고와 부족량을 계산합니다.
        BigDecimal predictedQtyD7 = input.predictedQtyD7();
        BigDecimal projectedD7 = availableQty;
        if (predictedQtyD7 != null) {
            projectedD7 = availableQty.subtract(predictedQtyD7).max(BigDecimal.ZERO);
        }

        BigDecimal predictedQtyD30 = input.predictedQtyD30();
        BigDecimal shortageQty30 = BigDecimal.ZERO;
        if (predictedQtyD30 != null && predictedQtyD30.compareTo(availableQty) > 0) {
            shortageQty30 = predictedQtyD30.subtract(availableQty);
        }

        BigDecimal safetyStock = input.safetyStockQty();
        BigDecimal safetyGap = BigDecimal.ZERO;
        if (safetyStock != null && safetyStock.compareTo(projectedD7) > 0) {
            safetyGap = safetyStock.subtract(projectedD7);
        }

        // 3. LOT 기반 소비기한 및 보유일수 계산
        Integer nearestExpiryDays = null;
        Integer maxHoldingDays = null;
        List<RiskReason> reasons = new ArrayList<>();

        if (input.lots() != null && !input.lots().isEmpty()) {
            for (RiskAssessmentInput.LotRiskItem lot : input.lots()) {
                boolean hasQuantity = lot.quantity() != null && lot.quantity().compareTo(BigDecimal.ZERO) > 0;
                if (hasQuantity && lot.expiryDate() != null) {
                    long days = ChronoUnit.DAYS.between(baseDate, lot.expiryDate());
                    int daysInt = (int) days;
                    if (nearestExpiryDays == null || daysInt < nearestExpiryDays) {
                        nearestExpiryDays = daysInt;
                    }
                }

                if (hasQuantity && lot.receivedDate() != null) {
                    long holding = ChronoUnit.DAYS.between(lot.receivedDate(), baseDate);
                    int holdingInt = (int) holding;
                    if (maxHoldingDays == null || holdingInt > maxHoldingDays) {
                        maxHoldingDays = holdingInt;
                    }
                }

                // 개별 LOT 단위 경과/임박 체크
                if (hasQuantity && isSaleStopped(lot, baseDate)) {
                    reasons.add(new RiskReason(
                            "LOT_SALE_STOPPED",
                            "판매중지일 도래 LOT 존재 (" + lot.lotNumber() + ")",
                            "CRITICAL",
                            "saleStopDate=" + lot.saleStopDate() + ", qty=" + lot.quantity()
                    ));
                }
                if (hasQuantity && isExpired(lot, baseDate)) {
                    reasons.add(new RiskReason(
                            "LOT_EXPIRED",
                            "소비기한 만료 LOT 존재 (" + lot.lotNumber() + ")",
                            "CRITICAL",
                            "expiryDate=" + lot.expiryDate() + ", qty=" + lot.quantity()
                    ));
                }
            }
        }

        // 4. 규칙 기반 위험 사유 평가
        // A. 소비기한 경계 평가
        if (nearestExpiryDays != null) {
            if (nearestExpiryDays <= 30 && nearestExpiryDays > 0) {
                reasons.add(new RiskReason(
                        "EXPIRY_CRITICAL",
                        "소비기한 30일 이하 임박 (" + nearestExpiryDays + "일 남음)",
                        "CRITICAL",
                        "nearestExpiryDays=" + nearestExpiryDays
                ));
            } else if (nearestExpiryDays <= 90 && nearestExpiryDays > 30) {
                reasons.add(new RiskReason(
                        "EXPIRY_WARNING",
                        "소비기한 90일 이하 주의 (" + nearestExpiryDays + "일 남음)",
                        "WARNING",
                        "nearestExpiryDays=" + nearestExpiryDays
                ));
            } else if (nearestExpiryDays <= 180 && nearestExpiryDays > 90) {
                reasons.add(new RiskReason(
                        "EXPIRY_NORMAL",
                        "소비기한 180일 이하 관리 (" + nearestExpiryDays + "일 남음)",
                        "NORMAL",
                        "nearestExpiryDays=" + nearestExpiryDays
                ));
            }
        }

        // B. 수요예측 기반 부족량 평가
        if (shortageQty30.compareTo(BigDecimal.ZERO) > 0) {
            reasons.add(new RiskReason(
                    "PREDICTED_SHORTAGE",
                    "D+30 수요예측 대비 재고 부족 예상 (" + shortageQty30 + "개 부족)",
                    "WARNING",
                    "predictedQtyD30=" + predictedQtyD30 + ", availableQty=" + availableQty
            ));
        }

        // C. 안전재고 미달 평가
        if (safetyStock != null && safetyStock.compareTo(BigDecimal.ZERO) > 0) {
            if (projectedD7.compareTo(safetyStock) < 0) {
                reasons.add(new RiskReason(
                        "PROJECTED_UNDER_SAFETY",
                        "D+7 예상잔고(" + projectedD7 + ")가 안전재고(" + safetyStock + ") 미만",
                        "CRITICAL",
                        "projectedD7=" + projectedD7 + ", safetyStock=" + safetyStock
                ));
            }
            if (availableQty.compareTo(safetyStock) < 0) {
                reasons.add(new RiskReason(
                        "CURRENT_UNDER_SAFETY",
                        "현재 가용재고(" + availableQty + ")가 안전재고(" + safetyStock + ") 미만",
                        "WARNING",
                        "availableQty=" + availableQty + ", safetyStock=" + safetyStock
                ));
            }
        }

        // 대표 사유와 화면의 세부 사유도 등급 우선순위와 동일한 순서로 제공합니다.
        reasons.sort((left, right) -> Integer.compare(
                severityRank(right.severity()),
                severityRank(left.severity())
        ));

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
                    "적정 재고 및 유효기한 유지 상태",
                    "GOOD",
                    "availableQty=" + availableQty
            ));
        }

        String primaryReason = reasons.get(0).message();

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

    private static boolean isExpired(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return "EXPIRED".equalsIgnoreCase(lot.lotStatus())
                || (lot.expiryDate() != null && !lot.expiryDate().isAfter(baseDate));
    }

    private static boolean isSaleStopped(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return "SALE_STOPPED".equalsIgnoreCase(lot.lotStatus())
                || "DEPLETED".equalsIgnoreCase(lot.lotStatus())
                || (lot.saleStopDate() != null && !lot.saleStopDate().isAfter(baseDate));
    }

    private RiskAssessmentResult unassessed(
            String code,
            String message,
            String evidence,
            LocalDate baseDate,
            Instant assessedAt
    ) {
        return new RiskAssessmentResult(
                "UNASSESSED",
                null,
                "UNASSESSED",
                message,
                List.of(new RiskReason(code, message, "WARNING", evidence)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                baseDate,
                assessedAt,
                RULE_VERSION
        );
    }

    private RiskAssessmentResult stale(
            String code,
            String message,
            String evidence,
            LocalDate baseDate,
            Instant assessedAt
    ) {
        return new RiskAssessmentResult(
                "STALE",
                null,
                "UNASSESSED",
                message,
                List.of(new RiskReason(code, message, "WARNING", evidence)),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                baseDate,
                assessedAt,
                RULE_VERSION
        );
    }
}
