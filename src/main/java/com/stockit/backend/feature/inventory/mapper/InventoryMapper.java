package com.stockit.backend.feature.inventory.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.stockit.backend.feature.inventory.vo.InventoryItemVO;
import com.stockit.backend.feature.inventory.vo.InventoryLotVO;
import com.stockit.backend.feature.inventory.vo.InventoryOptionVO;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;
import com.stockit.backend.feature.inventory.vo.InventorySummaryVO;

@Mapper
public interface InventoryMapper {

    List<InventoryItemVO> selectInventoryList(InventoryQuery query);

    long countInventory(InventoryQuery query);

    InventorySummaryVO selectInventorySummary(InventoryQuery query);

    List<InventoryOptionVO> selectChannelOptions();

    List<InventoryOptionVO> selectSalesPointOptions();

    List<InventoryOptionVO> selectWarehouseOptions();

    List<InventoryOptionVO> selectRegionOptions();

    List<InventoryOptionVO> selectCategoryOptions();

    List<InventoryOptionVO> selectStorageTypeOptions();

    InventoryItemVO selectInventoryDetail(String skuCode, String salesPointCode, java.time.LocalDate asOfDate);

    List<InventoryLotVO> selectInventoryLots(String skuCode, String salesPointCode, java.time.LocalDate asOfDate);
}
