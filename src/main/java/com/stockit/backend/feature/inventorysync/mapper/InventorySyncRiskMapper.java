package com.stockit.backend.feature.inventorysync.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventorysync.risk.InventorySyncRiskWriter.RiskPersistenceRecord;

@Mapper
public interface InventorySyncRiskMapper {
    int mergeRiskAssessments(@Param("records") List<RiskPersistenceRecord> records);

    int logicalDeleteSiblingAssessments(@Param("inventoryBalanceIds") List<Long> inventoryBalanceIds,
                                        @Param("actorId") Long actorId);
}
