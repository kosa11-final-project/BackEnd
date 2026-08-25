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
    public static final String RULE_VERSION = "v1.5.0";
    private static final BigDecimal CAUTION_STOCK_DAYS = BigDecimal.valueOf(14);
    private static final int CRITICAL_SALE_STOP_DAYS = 7;
    private static final int WARNING_SALE_STOP_DAYS = 30;
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

        // 3. LOT 기반 소비기한 및 보유일수 계산
        Integer nearestExpiryDays = null;
        Integer nearestSaleStopDays = null;
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
                            ? "수요예측 없이 현재 재고·안전재고·LOT 기준으로 판정했습니다."
                            : "수요예측 값이 유효하지 않아 현재 재고·안전재고·LOT 기준으로 판정했습니다.",
                    "INFO",
                    forecastMissing ? "forecastAvailable=false" : "forecast values are invalid"
            ));
        }

        if (inventoryMissing) {
            reasons.add(new RiskReason(
                    "DATA_MISSING",
                    "가용재고 데이터가 없어 재고 0개로 간주하여 위험 판정했습니다.",
                    "CRITICAL",
                    "on_hand_qty is null"
            ));
        } else if (availableQty.signum() == 0) {
            reasons.add(new RiskReason(
                    "ZERO_AVAILABLE_STOCK",
                    "판매 가능한 가용재고가 0개입니다.",
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

                if (sellable && lot.saleStopDate() != null) {
                    long days = ChronoUnit.DAYS.between(assessmentDate, lot.saleStopDate());
                    int daysInt = (int) days;
                    if (nearestSaleStopDays == null || daysInt < nearestSaleStopDays) {
                        nearestSaleStopDays = daysInt;
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
                    "수요예측과 안전재고 정책이 모두 없어 양호 여부를 확정할 기준이 부족합니다.",
                    "NORMAL",
                    "safetyStockQty=null"
            ));
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

        // B. 아직 판매 가능한 LOT의 판매중지일 임박 평가
        if (nearestSaleStopDays != null) {
            if (nearestSaleStopDays <= CRITICAL_SALE_STOP_DAYS) {
                reasons.add(new RiskReason(
                        "SALE_STOP_CRITICAL",
                        "판매중지일까지 7일 이하 남았습니다 (" + nearestSaleStopDays + "일 남음)",
                        "CRITICAL",
                        "nearestSaleStopDays=" + nearestSaleStopDays
                ));
            } else if (nearestSaleStopDays <= WARNING_SALE_STOP_DAYS) {
                reasons.add(new RiskReason(
                        "SALE_STOP_WARNING",
                        "판매중지일까지 30일 이하 남았습니다 (" + nearestSaleStopDays + "일 남음)",
                        "WARNING",
                        "nearestSaleStopDays=" + nearestSaleStopDays
                ));
            }
        }

        // C. 수요예측 기반 부족량 평가
        if (shortageQty30 != null && shortageQty30.compareTo(BigDecimal.ZERO) > 0) {
            boolean cautionShortage = availableQty.multiply(BigDecimal.valueOf(30))
                    .compareTo(predictedQtyD30.multiply(CAUTION_STOCK_DAYS)) < 0;
            reasons.add(new RiskReason(
                    cautionShortage ? "PREDICTED_SHORTAGE" : "PREDICTED_SHORTAGE_MONITORING",
                    cautionShortage
                            ? "D+30 수요예측 기준 재고일수가 14일 미만입니다 (" + shortageQty30 + "개 부족 예상)"
                            : "D+30 수요예측 대비 보충 검토가 필요합니다 (" + shortageQty30 + "개 부족 예상)",
                    cautionShortage ? "WARNING" : "NORMAL",
                    "predictedQtyD30=" + predictedQtyD30 + ", availableQty=" + availableQty
            ));
        }

        // D. 안전재고 미달 평가
        if (safetyStock != null && safetyStock.compareTo(BigDecimal.ZERO) > 0) {
            if (projectedD7 != null && projectedD7.compareTo(safetyStock) < 0) {
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
                    "판매 가능 LOT 기준 적정 재고 및 유효기한 유지 상태",
                    "GOOD",
                    "availableQty=" + availableQty
            ));
        }

        reasons.addAll(excludedLotReasons);
        reasons.sort((left, right) -> Integer.compare(
                severityRank(right.severity()),
                severityRank(left.severity())
        ));

        String primaryReason = reasons.get(0).message();
        String forecastNote = forecastNote(input, forecastUsable);
        if (forecastNote != null) {
            primaryReason += " " + forecastNote;
        }
        if (safetyStock == null) {
            primaryReason += " 안전재고 정책이 없어 안전재고 규칙은 제외했습니다.";
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
                || input.predictedQtyD30() == null) {
            return false;
        }
        return input.predictedQtyD7().compareTo(BigDecimal.ZERO) >= 0
                && input.predictedQtyD30().compareTo(input.predictedQtyD7()) >= 0;
    }

    private static String forecastNote(RiskAssessmentInput input, boolean forecastUsable) {
        if (forecastUsable && input.forecastStale()) {
            return "기준일이 오래된 수요예측도 현재 확보된 값으로 적용했습니다.";
        }
        if (!forecastUsable && !input.forecastAvailable()) {
            return "수요예측이 없어 재고·안전재고·LOT 규칙만 적용했습니다.";
        }
        if (!forecastUsable) {
            return "수요예측 값이 유효하지 않아 예측 기반 규칙은 제외했습니다.";
        }
        return null;
    }

    private static boolean isExpired(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return "EXPIRED".equalsIgnoreCase(lot.lotStatus())
                || (lot.expiryDate() != null && !lot.expiryDate().isAfter(baseDate));
    }

    private static boolean isSaleStopped(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return "SALE_STOPPED".equalsIgnoreCase(lot.lotStatus())
                || (lot.saleStopDate() != null && !lot.saleStopDate().isAfter(baseDate));
    }

    private static boolean isDepleted(RiskAssessmentInput.LotRiskItem lot) {
        return "DEPLETED".equalsIgnoreCase(lot.lotStatus());
    }

    private static boolean isSellable(RiskAssessmentInput.LotRiskItem lot, LocalDate baseDate) {
        return !isExpired(lot, baseDate) && !isSaleStopped(lot, baseDate) && !isDepleted(lot);
    }

}
