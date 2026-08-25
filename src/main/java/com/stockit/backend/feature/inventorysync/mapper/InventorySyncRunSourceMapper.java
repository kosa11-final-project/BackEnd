package com.stockit.backend.feature.inventorysync.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventorySyncRunSourceMapper {
    int insertPendingSources(@Param("runId") Long runId);
    int insertRunSource(@Param("runId") Long runId, @Param("sourceType") String sourceType, @Param("startVersion") long startVersion);
    int updateProgress(@Param("runId") Long runId, @Param("sourceType") String sourceType,
                       @Param("readCount") long readCount, @Param("mappedCount") long mappedCount);
    int completeSource(@Param("runId") Long runId, @Param("sourceType") String sourceType,
                       @Param("endVersion") long endVersion, @Param("status") String status);
    int incrementChanged(@Param("runId") Long runId, @Param("sourceType") String sourceType, @Param("changedCount") long changedCount);
    int markFailed(@Param("runId") Long runId);
}
