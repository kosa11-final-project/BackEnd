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
    void persistsRiskFromSellableLotsAndRecordsExcludedLotQuantity() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        LocalDate baseDate = LocalDate.of(2026, 8, 20);
        RiskAssessmentInput.LotRiskItem expired = new RiskAssessmentInput.LotRiskItem(
                "1", "LOT-EXPIRED", baseDate.minusDays(1), null, baseDate.minusDays(30),
                BigDecimal.valueOf(40), "EXPIRED"
        );
        RiskAssessmentInput.LotRiskItem sellable = new RiskAssessmentInput.LotRiskItem(
                "2", "LOT-SELLABLE", baseDate.plusDays(365), null, baseDate.minusDays(3),
                BigDecimal.valueOf(60), "AVAILABLE"
        );
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "GREETING", BigDecimal.valueOf(100), BigDecimal.TEN,
                BigDecimal.valueOf(40), BigDecimal.valueOf(30), baseDate,
                List.of(expired, sellable), true, false, baseDate
        );

        var records = writer.evaluateAndPersist(10L, 7L, Set.of("1:GREETING"), scopes -> List.of(
                new InventorySyncRiskWriter.RiskScopeSnapshot(100L, 200L, input)
        ));

        var record = records.get(0);
        assertEquals("GOOD", record.riskGrade());
        assertEquals("N", record.shortageYn());
        assertEquals(0, record.stockDays().compareTo(new BigDecimal("45.00")));
        org.junit.jupiter.api.Assertions.assertEquals(
                "현재 판매 가능 재고 60개가 안전재고 30개를 충족하고, 30일 예상 폐기수량은 0개이며 90일 이내 장기 과잉재고도 예상되지 않습니다.",
                record.reasonMessage());
        verify(mapper).mergeRiskAssessments(anyList());
    }

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
        org.junit.jupiter.api.Assertions.assertTrue(records.get(0).reasonMessage().contains("약 3일 후 재고가 소진될 것으로 예상됩니다"));
        org.junit.jupiter.api.Assertions.assertFalse(records.get(0).reasonMessage().contains("산식:"));
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
    void persistsACompletedServerRuleAssessmentWithoutForecastWhenSafetyPolicyExists() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "DEPT-1", BigDecimal.TEN, null, null, BigDecimal.ONE,
                LocalDate.of(2026, 8, 20), List.of(), false, false
        );

        var records = writer.evaluateAndPersist(10L, 7L, Set.of("1:UNASSIGNED"), scopes -> List.of(
                new InventorySyncRiskWriter.RiskScopeSnapshot(100L, null, input)
        ));

        assertEquals("GOOD", records.get(0).riskGrade());
        assertEquals("N", records.get(0).shortageYn());
        org.junit.jupiter.api.Assertions.assertNull(records.get(0).stockDays());
        org.junit.jupiter.api.Assertions.assertEquals(
                "현재 판매 가능 재고 10개가 안전재고 1개를 충족합니다.", records.get(0).reasonMessage());
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
        assertEquals("WARNING", records.get(0).riskGrade());
    }

    @Test
    void doesNotMarkShortageWhenThereIsNoCurrentSafetyStockPolicy() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);
        RiskAssessmentInput input = new RiskAssessmentInput(
                "SKU-1", "UNASSIGNED", BigDecimal.ZERO, null, null, null,
                LocalDate.of(2026, 8, 20), List.of(), false, false
        );

        var records = writer.evaluateAndPersist(10L, 7L, Set.of("1:UNASSIGNED"), scopes -> List.of(
                new InventorySyncRiskWriter.RiskScopeSnapshot(100L, null, input)
        ));

        assertEquals("N", records.get(0).shortageYn());
    }

    @Test
    void failsInsteadOfSilentlyCompletingWhenAffectedRiskSnapshotIsEmpty() {
        InventorySyncRiskMapper mapper = Mockito.mock(InventorySyncRiskMapper.class);
        InventorySyncRiskWriter writer = new InventorySyncRiskWriter(new RiskRuleEngine(), mapper);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> writer.evaluateAndPersist(
                10L, 7L, Set.of("1:GREETING"), scopes -> List.of()));
    }
}
