package com.stockit.backend.feature.inventorysync.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(InventorySyncRiskWriter.class);
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
        return evaluateAndPersist(
                runId,
                actorId,
                affectedScopes,
                LocalDate.now(BUSINESS_ZONE),
                Instant.now(java.time.Clock.system(BUSINESS_ZONE)),
                snapshotLoader
        );
    }

    @Transactional
    public List<RiskPersistenceRecord> evaluateAndPersist(
            Long runId,
            Long actorId,
            Set<String> affectedScopes,
            LocalDate asOfDate,
            RiskScopeSnapshotLoader snapshotLoader
    ) {
        return evaluateAndPersist(runId, actorId, affectedScopes, asOfDate,
                Instant.now(java.time.Clock.system(BUSINESS_ZONE)), snapshotLoader);
    }

    /** 하나의 run 시작시각을 모든 위험 레코드에 주입합니다. */
    @Transactional
    public List<RiskPersistenceRecord> evaluateAndPersist(
            Long runId,
            Long actorId,
            Set<String> affectedScopes,
            LocalDate asOfDate,
            Instant assessmentInstant,
            RiskScopeSnapshotLoader snapshotLoader
    ) {
        if (assessmentInstant == null) {
            throw new IllegalArgumentException("assessmentInstant is required");
        }
        long totalStarted = System.nanoTime();
        // loader는 balance·policy·LOT·latest forecast를 하나의 set-based SELECT snapshot으로 반환해야 합니다.
        long snapshotStarted = System.nanoTime();
        List<RiskScopeSnapshot> snapshots = snapshotLoader.load(affectedScopes, asOfDate, assessmentInstant);
        long snapshotDurationMs = elapsedMillis(snapshotStarted);
        if (affectedScopes != null && !affectedScopes.isEmpty() && snapshots.isEmpty()) {
            throw new IllegalStateException("risk snapshot is empty for affected scopes");
        }
        long evaluateStarted = System.nanoTime();
        List<EvaluatedPersistence> evaluated = snapshots.stream()
                .map(snapshot -> toPersistence(runId, actorId, snapshot, assessmentInstant))
                .toList();
        long evaluateDurationMs = elapsedMillis(evaluateStarted);
        List<RiskPersistenceRecord> records = evaluated.stream().map(EvaluatedPersistence::record).toList();
        int invalidForecastScopeCount = (int) evaluated.stream()
                .filter(item -> RiskRuleEngine.FORECAST_INVALID.equals(item.forecastUsability()))
                .count();
        List<Long> siblingIds = records.stream()
                .flatMap(record -> record.siblingInventoryBalanceIds().stream())
                .distinct()
                .toList();
        long writeStarted = System.nanoTime();
        int siblingDeleteBatchCount = 0;
        for (int start = 0; start < siblingIds.size(); start += WRITE_BATCH_SIZE) {
            siblingDeleteBatchCount++;
            mapper.logicalDeleteSiblingAssessments(
                    siblingIds.subList(start, Math.min(start + WRITE_BATCH_SIZE, siblingIds.size())),
                    actorId
            );
        }
        int riskWriteBatchCount = 0;
        for (int start = 0; start < records.size(); start += WRITE_BATCH_SIZE) {
            riskWriteBatchCount++;
            mapper.mergeRiskAssessments(records.subList(start, Math.min(start + WRITE_BATCH_SIZE, records.size())));
        }
        long writeDurationMs = elapsedMillis(writeStarted);
        log.info("inventory risk assessment completed: runId={}, ruleVersion={}, candidateScopeCount={}, "
                        + "snapshotRowCount={}, evaluatedScopeCount={}, writtenScopeCount={}, invalidForecastScopeCount={}, "
                        + "snapshotMs={}, evaluateMs={}, writeMs={}, totalMs={}, siblingDeleteBatches={}, riskWriteBatches={}",
                runId, RiskRuleEngine.RULE_VERSION, affectedScopes == null ? 0 : affectedScopes.size(), snapshots.size(),
                evaluated.size(), records.size(), invalidForecastScopeCount, snapshotDurationMs, evaluateDurationMs,
                writeDurationMs, elapsedMillis(totalStarted), siblingDeleteBatchCount, riskWriteBatchCount);
        return records;
    }

    private EvaluatedPersistence toPersistence(Long runId, Long actorId, RiskScopeSnapshot snapshot,
                                               Instant assessmentInstant) {
        RiskAssessmentResult result = ruleEngine.evaluate(snapshot.input(), assessmentInstant);
        // 유효한 입력은 항상 네 등급 중 하나로 확정되어야 합니다.
        // 음수 수량처럼 잘못된 입력은 엔진에서 예외를 발생시켜 동기화 자체를 중단합니다.
        if (result.dbRiskGrade() == null) {
            throw new IllegalStateException("risk grade was not resolved: " + result.assessmentStatus());
        }
        String grade = result.dbRiskGrade();
        // v1.7부터는 엔진이 날짜·수량·비율을 채운 사용자용 canonical 문장을 직접 생성합니다.
        // 저장 계층에서 헤더/evidence를 덧붙이면 목록과 상세가 갈라지므로 그대로 저장합니다.
        String reason = requireUtf8Length(result.primaryReason(), 1000);
        RiskPersistenceRecord record = new RiskPersistenceRecord(
                snapshot.inventoryBalanceId(), snapshot.forecastId(), grade,
                safetyStockShortageYn(result), stockDays(result, snapshot.input()),
                result.nearestExpiryDays(), result.maxHoldingDays(),
                result.ruleVersion(), reason, score(grade), runId, actorId, snapshot.siblingInventoryBalanceIds()
        );
        return new EvaluatedPersistence(record, result.forecastUsability());
    }

    /** RISK_ASSESSMENT.shortage_yn의 DB 계약(현재 판매 가능 재고가 안전재고보다 낮은지)을 따릅니다. */
    private static String safetyStockShortageYn(RiskAssessmentResult result) {
        return result.isCurrentStockUnderSafety() ? "Y" : "N";
    }

    private static BigDecimal stockDays(RiskAssessmentResult result, RiskAssessmentInput input) {
        if (!RiskRuleEngine.FORECAST_VALID.equals(result.forecastUsability())) {
            return null;
        }
        BigDecimal availableQty = result.availableQty();
        BigDecimal predictedQtyD30 = input.predictedQtyD30();
        if (availableQty == null
                || predictedQtyD30 == null
                || predictedQtyD30.signum() <= 0) {
            return null;
        }
        return availableQty.multiply(BigDecimal.valueOf(30))
                .divide(predictedQtyD30, 2, RoundingMode.HALF_UP);
    }

    private static int score(String grade) {
        return switch (grade) {
            case "CRITICAL" -> 100;
            case "WARNING" -> 70;
            case "NORMAL" -> 40;
            default -> 0;
        };
    }

    private static String requireUtf8Length(String value, int maxBytes) {
        String normalized = value == null || value.isBlank() ? "규칙 판정 결과가 없습니다." : value.trim();
        int byteLength = normalized.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > maxBytes) {
            throw new IllegalArgumentException("risk reason exceeds " + maxBytes + " UTF-8 bytes: " + byteLength);
        }
        return normalized;
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    @FunctionalInterface
    public interface RiskScopeSnapshotLoader {
        List<RiskScopeSnapshot> load(Set<String> affectedScopes);

        default List<RiskScopeSnapshot> load(Set<String> affectedScopes, LocalDate asOfDate) {
            return load(affectedScopes);
        }

        /** 같은 run의 시작시각을 snapshot forecast cutoff으로 전달합니다. */
        default List<RiskScopeSnapshot> load(
                Set<String> affectedScopes,
                LocalDate asOfDate,
                Instant assessmentInstant
        ) {
            return load(affectedScopes, asOfDate);
        }
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

    private record EvaluatedPersistence(RiskPersistenceRecord record, String forecastUsability) { }
}
