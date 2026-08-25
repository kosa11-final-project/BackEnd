package com.stockit.backend.feature.strategy.calculation.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationCostVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationInventoryVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPolicyVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationPriceVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSalesPointVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationSkuVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferCostPolicyVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationTransferRouteVO;
import com.stockit.backend.feature.strategy.calculation.vo.StrategyCalculationWarehouseRouteVO;

/** 계산 시점의 전략 입력을 원본 grain 그대로 조회한다. */
@Mapper
public interface StrategyCalculationInputMapper {

    StrategyCalculationSkuVO selectActiveSku(@Param("skuId") Long skuId);

    List<StrategyCalculationInventoryVO> selectInventory(
            @Param("skuId") Long skuId
    );

    List<StrategyCalculationSalesPointVO> selectActiveSalesPoints(
            @Param("salesPointIds") List<Long> salesPointIds
    );

    List<StrategyCalculationPriceVO> selectEffectivePrices(
            @Param("skuId") Long skuId,
            @Param("salesPointIds") List<Long> salesPointIds,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<StrategyCalculationCostVO> selectEffectiveCosts(
            @Param("skuId") Long skuId,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<StrategyCalculationPolicyVO> selectEffectivePolicies(
            @Param("skuId") Long skuId,
            @Param("asOfDate") LocalDate asOfDate
    );

    List<StrategyCalculationWarehouseRouteVO> selectActiveWarehouseRoutes(
            @Param("salesPointIds") List<Long> salesPointIds
    );

    List<StrategyCalculationTransferRouteVO> selectActiveTransferRoutes();

    List<StrategyCalculationTransferCostPolicyVO> selectTransferCostPolicies(
            @Param("rangeStartDate") LocalDate rangeStartDate,
            @Param("rangeEndDate") LocalDate rangeEndDate
    );
}
