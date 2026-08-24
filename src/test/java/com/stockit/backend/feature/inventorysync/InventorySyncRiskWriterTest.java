package com.stockit.backend.feature.inventorysync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.stockit.backend.feature.inventory.mapper.RiskAssessmentMapper;
import com.stockit.backend.feature.inventory.risk.RiskAssessmentInput;
import com.stockit.backend.feature.inventory.risk.RiskRuleEngine;
import com.stockit.backend.feature.inventorysync.mapper.InventorySyncRiskMapper;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskWriter;

class InventorySyncRiskWriterTest {
    @Test
    void ruleGradeAndDeterministicFormulaArePersistedFromSetBasedSnapshot() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "GREETING", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                LocalDate.of(2026, 8, 20), List.of(), true, false
        );

        List<InventorySyncRiskWriter.RiskPersistenceRecord> records = writer.evaluateAndPersist(
                10L, 7L, Set.of("1:GREETING"), scopes -> List.of(
                        new InventorySyncRiskWriter.RiskScopeSnapshot(100L, 200L, input)
                )
        );

        assertEquals("CRITICAL", records.get(0).riskGrade());
        assertEquals("Y", records.get(0).shortageYn());
        assertEquals(0, records.get(0).stockDays().compareTo(new BigDecimal("3")));
        org.junit.jupiter.api.Assertions.assertTrue(records.get(0).reasonMessage().contains("산식:"));
        org.junit.jupiter.api.Assertions.assertTrue(records.get(0).reasonMessage().contains("가용재고=on_hand_qty"));
        verify(mapper).mergeRiskAssessments(anyList());
    }

    @Test
    void logicallyDeletesNonAnchorRiskRowsBeforePersistingTheScopeAnchor() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "DEPT-1", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                LocalDate.of(2026, 8, 20), List.of(), true, false
        );

        writer.evaluateAndPersist(10L, 7L, Set.of("1:DEPT-1"), scopes -> List.of(
                new InventorySyncRiskWriter.RiskScopeSnapshot(100L, 200L, input, List.of(101L, 102L))
        ));

        verify(mapper).logicalDeleteSiblingAssessments(List.of(101L, 102L), 7L);
        verify(mapper).mergeRiskAssessments(anyList());
    }

    @Test
    void deletesSiblingRiskRowsInOracleSafeBatches() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "DEPT-1", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                LocalDate.of(2026, 8, 20), List.of(), true, false
        );
        List<Long> siblingIds = LongStream.rangeClosed(1, 1001).boxed().toList();

        writer.evaluateAndPersist(10L, 7L, Set.of("1:DEPT-1"), scopes -> List.of(
                new InventorySyncRiskWriter.RiskScopeSnapshot(100L, 200L, input, siblingIds)
        ));

        verify(mapper, times(3)).logicalDeleteSiblingAssessments(
                Mockito.argThat(ids -> !ids.isEmpty() && ids.size() <= 500),
                Mockito.eq(7L)
        );
    }

    @Test
    void persistsACompletedServerRuleAssessmentWithoutForecast() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "UNASSIGNED", BigDecimal.TEN, null, null, BigDecimal.ONE,
                LocalDate.of(2026, 8, 20), List.of(), false, false
        );

        var records = writer.evaluateAndPersist(10L, 7L, Set.of("1:UNASSIGNED"), scopes -> List.of(
                new InventorySyncRiskWriter.RiskScopeSnapshot(100L, null, input)
        ));

        assertEquals("NORMAL", records.get(0).riskGrade());
        assertEquals("N", records.get(0).shortageYn());
        org.junit.jupiter.api.Assertions.assertNull(records.get(0).stockDays());
        org.junit.jupiter.api.Assertions.assertTrue(records.get(0).reasonMessage().contains("수요예측이 없어"));
        verify(mapper).mergeRiskAssessments(anyList());
    }

    @Test
    void storesShortageFlagFromSafetyStockInsteadOfForecastShortage() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "DEPT-1", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(20), BigDecimal.ONE,
                LocalDate.of(2026, 8, 20), List.of(), true, false
        );

        var records = writer.evaluateAndPersist(10L, 7L, Set.of("1:10"), scopes -> List.of(
                new InventorySyncRiskWriter.RiskScopeSnapshot(100L, 200L, input)
        ));

        assertEquals("N", records.get(0).shortageYn());
        assertEquals("NORMAL", records.get(0).riskGrade());
    }

    @Test
    void failsInsteadOfSilentlyCompletingWhenAffectedRiskSnapshotIsEmpty() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> writer.evaluateAndPersist(
                10L, 7L, Set.of("1:GREETING"), scopes -> List.of()));
    }
}
