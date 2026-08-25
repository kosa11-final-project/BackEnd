package com.stockit.backend.feature.inventory.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventory.vo.InventoryItemVO;
import com.stockit.backend.feature.inventory.vo.InventoryLotVO;
import com.stockit.backend.feature.inventory.vo.InventoryOptionVO;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;
import com.stockit.backend.feature.inventory.vo.InventorySummaryVO;
import com.stockit.backend.feature.inventory.vo.SkuChannelPriceVO;

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

    InventoryItemVO selectInventoryDetail(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode,
            @Param("asOfDate") LocalDate asOfDate
    );

    int countInventoryScope(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode
    );

    List<InventoryLotVO> selectInventoryLots(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<SkuChannelPriceVO> selectSkuChannelPrices(
            @Param("skuCode") String skuCode,
            @Param("asOfDate") LocalDate asOfDate
    );
}
