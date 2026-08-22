package com.stockit.backend.feature.inventorysync.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventorysync.adapter.CanonicalInventoryRecord;

/** 원천 4종을 page 단위로 읽는 production projection mapper입니다. */
@Mapper
public interface InventorySyncSourcePageMapper {

    List<CanonicalInventoryRecord> selectCanonicalPage(
            @Param("sourceType") String sourceType,
            @Param("sourceVersion") long sourceVersion,
            @Param("lastSourceRecordKey") String lastSourceRecordKey,
            @Param("limit") int limit
    );

    int countActiveRows(@Param("sourceType") String sourceType, @Param("sourceVersion") long sourceVersion);
    int countInvalidOrUnmapped(@Param("sourceType") String sourceType);
}
