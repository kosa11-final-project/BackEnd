package com.stockit.backend.feature.inventorysync.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;

@Mapper
public interface InventorySyncCanonicalMapper {

    int updateProducts(@Param("records") List<CanonicalInventoryRecord> records, @Param("actorId") Long actorId);
    int updateSkus(@Param("records") List<CanonicalInventoryRecord> records, @Param("actorId") Long actorId);
    int updatePrices(@Param("records") List<CanonicalInventoryRecord> records, @Param("actorId") Long actorId);
    int updateSkuCosts(@Param("records") List<CanonicalInventoryRecord> records, @Param("actorId") Long actorId);
    int updateLots(@Param("records") List<CanonicalInventoryRecord> records, @Param("actorId") Long actorId);
    int updatePolicies(@Param("records") List<CanonicalInventoryRecord> records, @Param("actorId") Long actorId);
    int updateInventoryBalances(@Param("records") List<CanonicalInventoryRecord> records, @Param("actorId") Long actorId);
}
