package com.stockit.backend.feature.inventorysync.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventorysync.vo.InventorySyncRunSourceVO;
import com.stockit.backend.feature.inventorysync.vo.InventorySyncSourceStateVO;

@Mapper
public interface InventorySyncStateQueryMapper {
    List<InventorySyncSourceStateVO> selectSourceStates();
    List<InventorySyncRunSourceVO> selectRunSources(@Param("runId") Long runId);
}
