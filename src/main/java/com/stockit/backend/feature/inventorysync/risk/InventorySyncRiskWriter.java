package com.stockit.backend.feature.inventorysync.risk;

import java.math.BigDecimal;
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
        // 기존 RISK_ASSESSMENT의 risk_grade 제약은 CRITICAL/WARNING/NORMAL/GOOD만
        // 허용하므로 UNASSESSED/STALE 상태는 WARNING으로 보존하고 실제 상태는
        // reason_message에 명시합니다.
        String grade = result.dbRiskGrade() == null ? "WARNING" : result.dbRiskGrade();
        BigDecimal shortage = result.shortageQty30() == null ? BigDecimal.ZERO : result.shortageQty30();
        String ruleCode = result.reasons() == null || result.reasons().isEmpty()
                ? "RULE_EVALUATION" : result.reasons().get(0).code();
        String reason = truncate(serverReason(result, ruleCode), 1000);
        return new RiskPersistenceRecord(
                snapshot.inventoryBalanceId(), snapshot.forecastId(), grade,
                shortage.signum() > 0 ? "Y" : "N", result.nearestExpiryDays(), result.maxHoldingDays(),
                result.ruleVersion(), reason, score(grade), runId, actorId, snapshot.siblingInventoryBalanceIds()
        );
    }

    private static String serverReason(RiskAssessmentResult result, String ruleCode) {
        String status = result.assessmentStatus() == null ? "UNKNOWN" : result.assessmentStatus();
        String primary = result.primaryReason() == null ? "규칙 판정 결과가 없습니다." : result.primaryReason();
        if (result.availableQty() == null || result.safetyStockQty() == null || result.projectedD7() == null) {
            return "[" + status + "/" + result.ruleVersion() + "/" + ruleCode + "] " + primary;
        }
        BigDecimal available = result.availableQty();
        BigDecimal predictedD7 = result.projectedD7() == null
                ? BigDecimal.ZERO : result.projectedD7();
        BigDecimal safety = result.safetyStockQty();
        BigDecimal shortage30 = result.shortageQty30() == null ? BigDecimal.ZERO : result.shortageQty30();
        BigDecimal safetyGap = result.safetyGapQty() == null ? BigDecimal.ZERO : result.safetyGapQty();
        return "[" + status + "/" + result.ruleVersion() + "/" + ruleCode + "] " + primary
                + " | 산식: 가용재고=on_hand_qty(" + format(available) + ")"
                + ", D+7예상잔고=max(0, 가용재고-예측D7)=" + format(result.projectedD7())
                + ", D+30부족량=max(0, 예측D30-가용재고)=" + format(shortage30)
                + ", 안전재고부족=max(0, 안전재고-D+7예상잔고)=" + format(safetyGap)
                + ", 소비기한/LOT 규칙을 함께 적용했습니다.";
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
            Integer expiryDaysLeft, Integer holdingDays, String ruleVersion, String reasonMessage,
            int riskScore, Long runId, Long actorId, List<Long> siblingInventoryBalanceIds
    ) { }
}
