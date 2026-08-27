package com.stockit.backend.feature.inventorysync.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.inventory.risk.RiskAssessmentInput;
import com.stockit.backend.feature.inventory.risk.RiskAssessmentResult;
import com.stockit.backend.feature.inventory.risk.RiskRuleEngine;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRiskMapper;

/** 규칙 엔진 결과와 재현 가능한 산식을 같은 동기화 트랜잭션에 저장하는 writer입니다. */
@Component
public class InventorySyncRiskWriter {
    private static final int WRITE_BATCH_SIZE = 500;
    private final RiskRuleEngine ruleEngine;
    private final InventorySyncRiskMapper mapper;

    public InventorySyncRiskWriter(RiskRuleEngine ruleEngine, InventorySyncRiskMapper mapper) {
        this.ruleEngine = ruleEngine;
        this.mapper = mapper;
    }

    @Transactional
    public List<RiskPersistenceRecord> evaluateAndPersist(
            Long runId,
            Long actorId,
            Set<String> affectedScopes,
            RiskScopeSnapshotLoader snapshotLoader
    ) {
        // loader는 balance·policy·LOT·latest forecast를 하나의 set-based SELECT snapshot으로 반환해야 합니다.
        List<RiskScopeSnapshot> snapshots = snapshotLoader.load(affectedScopes);
        if (affectedScopes != null && !affectedScopes.isEmpty() && snapshots.isEmpty()) {
            throw new IllegalStateException("risk snapshot is empty for affected scopes");
        }
        List<RiskPersistenceRecord> records = snapshots.stream()
                .map(snapshot -> toPersistence(runId, actorId, snapshot))
                .toList();
        List<Long> siblingIds = records.stream()
                .flatMap(record -> record.siblingInventoryBalanceIds().stream())
                .distinct()
                .toList();
        for (int start = 0; start < siblingIds.size(); start += WRITE_BATCH_SIZE) {
            mapper.logicalDeleteSiblingAssessments(
                    siblingIds.subList(start, Math.min(start + WRITE_BATCH_SIZE, siblingIds.size())),
                    actorId
            );
        }
        for (int start = 0; start < records.size(); start += WRITE_BATCH_SIZE) {
            mapper.mergeRiskAssessments(records.subList(start, Math.min(start + WRITE_BATCH_SIZE, records.size())));
        }
        return records;
    }

    private RiskPersistenceRecord toPersistence(Long runId, Long actorId, RiskScopeSnapshot snapshot) {
        RiskAssessmentResult result = ruleEngine.evaluate(snapshot.input());
        // 유효한 입력은 항상 네 등급 중 하나로 확정되어야 합니다.
        // 음수 수량처럼 잘못된 입력은 엔진에서 예외를 발생시켜 동기화 자체를 중단합니다.
        if (result.dbRiskGrade() == null) {
            throw new IllegalStateException("risk grade was not resolved: " + result.assessmentStatus());
        }
        String grade = result.dbRiskGrade();
        String ruleCode = result.reasons() == null || result.reasons().isEmpty()
                ? "RULE_EVALUATION" : result.reasons().get(0).code();
        String reason = truncate(serverReason(result, ruleCode, snapshot.input()), 1000);
        return new RiskPersistenceRecord(
                snapshot.inventoryBalanceId(), snapshot.forecastId(), grade,
                safetyStockShortageYn(result), stockDays(result, snapshot.input()),
                result.nearestExpiryDays(), result.maxHoldingDays(),
                result.ruleVersion(), reason, score(grade), runId, actorId, snapshot.siblingInventoryBalanceIds()
        );
    }

    /** RISK_ASSESSMENT.shortage_yn의 DB 계약(현재 판매 가능 재고가 안전재고보다 낮은지)을 따릅니다. */
    private static String safetyStockShortageYn(RiskAssessmentResult result) {
        BigDecimal availableQty = result.availableQty();
        BigDecimal safetyStockQty = result.safetyStockQty();
        if (safetyStockQty == null) {
            return "N";
        }
        BigDecimal normalizedAvailableQty = availableQty == null ? BigDecimal.ZERO : availableQty;
        return normalizedAvailableQty.compareTo(safetyStockQty) < 0 ? "Y" : "N";
    }

    private static BigDecimal stockDays(RiskAssessmentResult result, RiskAssessmentInput input) {
        BigDecimal availableQty = result.availableQty();
        BigDecimal predictedQtyD30 = input.predictedQtyD30();
        if (result.shortageQty30() == null
                || availableQty == null
                || predictedQtyD30 == null
                || predictedQtyD30.signum() <= 0) {
            return null;
        }
        return availableQty.multiply(BigDecimal.valueOf(30))
                .divide(predictedQtyD30, 2, RoundingMode.HALF_UP);
    }

    private static String serverReason(RiskAssessmentResult result, String ruleCode, RiskAssessmentInput input) {
        String status = result.assessmentStatus() == null ? "UNKNOWN" : result.assessmentStatus();
        String primary = result.primaryReason() == null ? "규칙 판정 결과가 없습니다." : result.primaryReason();
        StringBuilder reason = new StringBuilder("[")
                .append(status).append('/').append(result.ruleVersion()).append('/').append(ruleCode).append("] ")
                .append(primary);
        if (result.availableQty() == null) {
            return reason.toString();
        }

        BigDecimal physicalAvailableQty = input.onHandQty() == null ? BigDecimal.ZERO : input.onHandQty();
        BigDecimal excludedLotQty = physicalAvailableQty.subtract(result.availableQty()).max(BigDecimal.ZERO);
        if (excludedLotQty.signum() > 0) {
            reason.append(" | 산식: 판매가능재고=on_hand_qty(")
                    .append(format(physicalAvailableQty)).append(")-판매제외LOT(")
                    .append(format(excludedLotQty)).append(")=")
                    .append(format(result.availableQty()));
        } else {
            reason.append(" | 산식: 가용재고=on_hand_qty(")
                    .append(format(result.availableQty())).append(')');
        }
        if (result.projectedD7() != null) {
            reason.append(", D+7예상잔고=max(0, 판매가능재고-예측D7)=")
                    .append(format(result.projectedD7()));
        }
        if (result.shortageQty30() != null) {
            reason.append(", D+30부족량=max(0, 예측D30-판매가능재고)=")
                    .append(format(result.shortageQty30()));
        }
        if (result.safetyStockQty() != null && result.safetyGapQty() != null) {
            reason.append(", 안전재고부족=max(0, 안전재고-D+7예상잔고)=")
                    .append(format(result.safetyGapQty()));
        }
        if (excludedLotQty.signum() > 0) {
            reason.append(", 판매 제외 LOT=").append(format(excludedLotQty));
        }
        reason.append(", 소비기한/LOT 규칙을 함께 적용했습니다.");
        return reason.toString();
    }

    private static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static int score(String grade) {
        return switch (grade) {
            case "CRITICAL" -> 100;
            case "WARNING" -> 70;
            case "NORMAL" -> 40;
            default -> 0;
        };
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return "규칙 판정 결과가 없습니다.";
        return value.length() <= max ? value : value.substring(0, max);
    }

    @FunctionalInterface
    public interface RiskScopeSnapshotLoader {
        List<RiskScopeSnapshot> load(Set<String> affectedScopes);
    }

    public record RiskScopeSnapshot(Long inventoryBalanceId, Long forecastId, RiskAssessmentInput input,
                                    List<Long> siblingInventoryBalanceIds) {
        public RiskScopeSnapshot {
            siblingInventoryBalanceIds = siblingInventoryBalanceIds == null ? List.of() : List.copyOf(siblingInventoryBalanceIds);
        }

        public RiskScopeSnapshot(Long inventoryBalanceId, Long forecastId, RiskAssessmentInput input) {
            this(inventoryBalanceId, forecastId, input, List.of());
        }
    }
    public record RiskPersistenceRecord(
            Long inventoryBalanceId, Long forecastId, String riskGrade, String shortageYn,
            BigDecimal stockDays, Integer expiryDaysLeft, Integer holdingDays,
            String ruleVersion, String reasonMessage,
            int riskScore, Long runId, Long actorId, List<Long> siblingInventoryBalanceIds
    ) { }
}
