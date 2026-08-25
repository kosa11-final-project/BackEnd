package com.stockit.backend.feature.inventorysync.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;

@Mapper
public interface InventorySyncSourceWriteMapper {
    Long lockSourceState(@Param("sourceType") String sourceType);
    int markOfflineSynced(@Param("records") List<CanonicalInventoryRecord> records, @Param("runId") Long runId);
    int markEcommerceSynced(@Param("records") List<CanonicalInventoryRecord> records, @Param("runId") Long runId);
    int markGreetingSynced(@Param("records") List<CanonicalInventoryRecord> records, @Param("runId") Long runId);
    int markWarehouseSynced(@Param("records") List<CanonicalInventoryRecord> records, @Param("runId") Long runId);
    int refreshState(@Param("sourceTypes") Set<String> sourceTypes, @Param("runId") Long runId);
}
