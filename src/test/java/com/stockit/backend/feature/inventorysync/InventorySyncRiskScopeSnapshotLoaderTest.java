package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskScopeSnapshotLoader;
import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskSnapshotMapper;

class InventorySyncRiskScopeSnapshotLoaderTest {

    @Test
    void findsDistinctScopesThatNeedTheRequestedRuleVersion() throws Exception {
        InventorySyncRiskSnapshotMapper mapper = org.mockito.Mockito.mock(InventorySyncRiskSnapshotMapper.class);
        when(mapper.selectScopesRequiringRuleVersion("v1.2.0"))
                .thenReturn(List.of("1:10", "1:10", "2:UNASSIGNED"));
        var loader = new InventorySyncRiskScopeSnapshotLoader(mapper);

        java.lang.reflect.Method method;
        try {
            method = InventorySyncRiskScopeSnapshotLoader.class
                    .getMethod("findScopesRequiringRuleVersion", String.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("규칙 버전이 바뀐 scope를 찾는 loader 메서드가 필요합니다.", exception);
        }

        assertThat(method.invoke(loader, "v1.2.0"))
                .isEqualTo(Set.of("1:10", "2:UNASSIGNED"));
    }

    @Test
    void aggregatesAllLotsInSkuSalesPointScopeAndSelectsStableAnchor() {
        InventorySyncRiskSnapshotMapper mapper = org.mockito.Mockito.mock(InventorySyncRiskSnapshotMapper.class);
        when(mapper.selectAffectedScopeSnapshot(anySet(), any(LocalDate.class))).thenReturn(List.of(
                row(20L, "SKU-1", "DEPT-1", "LOT-2", new BigDecimal("7")),
                row(10L, "SKU-1", "DEPT-1", "LOT-1", new BigDecimal("3"))
        ));

        var snapshots = new InventorySyncRiskScopeSnapshotLoader(mapper).load(Set.of("1:10"));

        assertThat(snapshots).hasSize(1);
        var snapshot = snapshots.get(0);
        assertThat(snapshot.inventoryBalanceId()).isEqualTo(10L);
        assertThat(snapshot.input().onHandQty()).isEqualByComparingTo("10");
        assertThat(snapshot.input().lots()).hasSize(2);
        assertThat(snapshot.siblingInventoryBalanceIds()).containsExactly(20L);
    }

    @Test
    void usesTheSyncPinnedDateForSnapshotSelectionAndAssessment() {
        InventorySyncRiskSnapshotMapper mapper = org.mockito.Mockito.mock(InventorySyncRiskSnapshotMapper.class);
        LocalDate asOfDate = LocalDate.of(2026, 8, 27);
        var row = row(10L, "SKU-1", "DEPT-1", "LOT-1", BigDecimal.TEN);
        row.setForecastBaseDate(asOfDate.minusDays(1));
        when(mapper.selectAffectedScopeSnapshot(Set.of("1:10"), asOfDate)).thenReturn(List.of(row));

        var snapshots = new InventorySyncRiskScopeSnapshotLoader(mapper).load(Set.of("1:10"), asOfDate);

        assertThat(snapshots).singleElement().satisfies(snapshot ->
                assertThat(snapshot.input().assessmentDate()).isEqualTo(asOfDate));
        verify(mapper).selectAffectedScopeSnapshot(Set.of("1:10"), asOfDate);
    }

    @Test
    void keepsUnassignedScopeForWarehouseCommonStock() {
        InventorySyncRiskSnapshotMapper mapper = org.mockito.Mockito.mock(InventorySyncRiskSnapshotMapper.class);
        var common = row(30L, "SKU-1", "UNASSIGNED", "LOT-COMMON", new BigDecimal("12"));
        common.setSkuId(1L);
        common.setSalesPointId(null);
        when(mapper.selectAffectedScopeSnapshot(anySet(), any(LocalDate.class))).thenReturn(List.of(common));

        var snapshots = new InventorySyncRiskScopeSnapshotLoader(mapper).load(Set.of("1:UNASSIGNED"));

        assertThat(snapshots).singleElement().extracting(snapshot -> snapshot.input().salesPointCode())
                .isEqualTo("UNASSIGNED");
    }

    private static InventorySyncRiskSnapshotMapper.RiskScopeRow row(
            Long balanceId, String skuCode, String salesPointCode, String lotNumber, BigDecimal onHandQty) {
        var row = new InventorySyncRiskSnapshotMapper.RiskScopeRow();
        row.setInventoryBalanceId(balanceId);
        row.setSkuId(1L);
        row.setSalesPointId("UNASSIGNED".equals(salesPointCode) ? null : 10L);
        row.setSkuCode(skuCode);
        row.setSalesPointCode(salesPointCode);
        row.setLotId(String.valueOf(balanceId));
        row.setLotNumber(lotNumber);
        row.setLotQty(onHandQty);
        row.setOnHandQty(onHandQty);
        row.setPredictedQtyD7(BigDecimal.ONE);
        row.setPredictedQtyD14(BigDecimal.valueOf(5));
        row.setPredictedQtyD30(BigDecimal.TEN);
        row.setSafetyStockQty(BigDecimal.ONE);
        row.setForecastBaseDate(LocalDate.now());
        row.setExpiryDate(LocalDate.now().plusDays(90));
        row.setReceivedDate(LocalDate.now().minusDays(3));
        return row;
    }
}
