package com.stockit.backend.feature.inventorysync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.dao.CannotAcquireLockException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.inventorysync.demo.InventoryDemoAdjustmentMapper;
import com.stockit.backend.feature.inventorysync.demo.InventoryDemoBulkAdjustmentRequest;
import com.stockit.backend.feature.inventorysync.demo.InventoryDemoAdjustmentRequest;
import com.stockit.backend.feature.inventorysync.demo.InventoryDemoAdjustmentService;
import com.stockit.backend.common.exception.AppException;

class InventoryDemoAdjustmentServiceTest {
    @Mock InventoryDemoAdjustmentMapper mapper;
    private InventoryDemoAdjustmentService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(InventoryDemoAdjustmentMapper.class);
        service = new InventoryDemoAdjustmentService(mapper, new ObjectMapper(), true);
        when(mapper.lockSourceState(anyString())).thenReturn(1);
        when(mapper.countRecentApplied(eq(7L), any(Instant.class))).thenReturn(0);
    }

    @Test
    void changesOnlySourceAndWritesOneAuditPerItem() {
        when(mapper.selectByRequestId("demo-1")).thenReturn(null);
        when(mapper.lockSourceRow("GREETING", "GREETING:1")).thenReturn(source(20, 4, "hash-before", "hash-before"));
        when(mapper.updateSource(eq("GREETING"), eq("GREETING:1"), eq(new BigDecimal("5")), anyString())).thenReturn(1);

        var response = service.apply(request("demo-1", "GREETING", "GREETING:1", "5"), 7L);

        assertEquals("APPLIED", response.status());
        assertEquals(new BigDecimal("15"), response.items().get(0).remainingQty());
        verify(mapper).updatePendingCount("GREETING", 1);
        verify(mapper).insertAudit(eq("demo-1"), anyString(), eq("GREETING"), eq("GREETING:1"), eq(new BigDecimal("5")), eq(4L), eq(5L), eq("hash-before"), anyString(), eq(7L), anyString());
    }

    @Test
    void insufficientStockFailsBeforeAnySourceOrAuditWrite() {
        when(mapper.selectByRequestId("demo-2")).thenReturn(null);
        when(mapper.lockSourceRow("WAREHOUSE", "W:1")).thenReturn(source(2, 1, "hash", "other"));

        assertThrows(AppException.class, () -> service.apply(request("demo-2", "WAREHOUSE", "W:1", "3"), 7L));
        verify(mapper, never()).updateSource(anyString(), anyString(), any(), anyString());
        verify(mapper, never()).insertAudit(anyString(), anyString(), anyString(), anyString(), any(), any(Long.class), any(Long.class), anyString(), anyString(), any(Long.class), anyString());
    }

    @Test
    void sameRequestIsIdempotent() {
        var existing = new InventoryDemoAdjustmentMapper.DemoAuditRow("demo-3", InventoryDemoAdjustmentService.requestHash(request("demo-3", "OFFLINE", "O:1", "1")), "APPLIED", 1, Instant.now());
        when(mapper.selectByRequestId("demo-3")).thenReturn(existing);

        var response = service.apply(request("demo-3", "OFFLINE", "O:1", "1"), 7L);

        assertEquals("APPLIED", response.status());
        verify(mapper, never()).lockSourceState(anyString());
    }

    @Test
    void lockWaitFailureIsReturnedAsInventoryConflict() {
        when(mapper.selectByRequestId("demo-lock")).thenReturn(null);
        when(mapper.lockSourceState("OFFLINE"))
                .thenThrow(new CannotAcquireLockException("ORA-30006: resource busy"));

        var exception = assertThrows(
                AppException.class,
                () -> service.apply(request("demo-lock", "OFFLINE", "O:1", "1"), 7L));

        assertEquals("INVENTORY_SYNC-001", exception.getErrorCode().getCode());
        verify(mapper, never()).lockSourceRow(anyString(), anyString());
    }

    @Test
    void bulkAdjustmentChangesEveryPositiveSyncedSourceRow() {
        when(mapper.selectByRequestId("demo-bulk-all")).thenReturn(null);
        for (String sourceType : InventorySyncSourceOrder.TYPES) {
            when(mapper.lockBulkSourceState(sourceType))
                    .thenReturn(new InventoryDemoAdjustmentMapper.BulkSourceStateRow(2, 0));
            when(mapper.countAdjustableSyncedRows(sourceType)).thenReturn(2);
            when(mapper.insertBulkAudit(
                    eq("demo-bulk-all"), anyString(), eq(sourceType), any(BigDecimal.class), eq(7L)
            )).thenReturn(2);
            when(mapper.updateAllSyncedSources(eq(sourceType), any(BigDecimal.class))).thenReturn(2);
            when(mapper.updatePendingCountBulk(sourceType, 2)).thenReturn(1);
        }

        var response = service.applyAll(
                new InventoryDemoBulkAdjustmentRequest("demo-bulk-all", BigDecimal.TEN),
                7L
        );

        assertEquals(8, response.appliedCount());
        assertEquals(0, response.alreadyPendingCount());
        assertEquals(4, response.sources().size());
    }

    private static InventoryDemoAdjustmentMapper.DemoSourceRow source(int onHand, int rowVersion, String hash, String syncedHash) {
        var source = new InventoryDemoAdjustmentMapper.DemoSourceRow();
        source.setOnHandQty(new BigDecimal(onHand));
        source.setReservedQty(BigDecimal.ZERO);
        source.setRowVersion(rowVersion);
        source.setRecordHash(hash);
        source.setSyncedRecordHash(syncedHash);
        return source;
    }

    private static InventoryDemoAdjustmentRequest request(String id, String type, String key, String qty) {
        return new InventoryDemoAdjustmentRequest(id, List.of(new InventoryDemoAdjustmentRequest.Item(type, key, new BigDecimal(qty))));
    }
}
