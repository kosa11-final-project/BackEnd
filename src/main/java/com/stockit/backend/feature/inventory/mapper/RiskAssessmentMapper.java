package com.stockit.backend.feature.inventory.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stockit.backend.feature.inventory.risk.RiskAssessmentInput.LotRiskItem;
import com.stockit.backend.feature.inventory.risk.InventoryQuantityVO;
import com.stockit.backend.feature.inventory.risk.PersistedRiskAssessmentVO;
import com.stockit.backend.feature.inventory.risk.RiskForecastVO;

@Mapper
public interface RiskAssessmentMapper {

    InventoryQuantityVO selectInventoryQuantities(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode
    );

    BigDecimal selectSafetyStock(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode,
            @Param("asOfDate") LocalDate asOfDate
    );

    RiskForecastVO selectLatestForecast(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode,
            @Param("asOfDate") LocalDate asOfDate
    );

    PersistedRiskAssessmentVO selectLatestPersistedAssessment(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode
    );

    List<LotRiskItem> selectLotRiskItems(
            @Param("skuCode") String skuCode,
            @Param("salesPointCode") String salesPointCode
    );

}
