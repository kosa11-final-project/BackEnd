package com.stockit.backend.feature.inventorysync.risk;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventorySyncRiskSnapshotMapper {
    List<RiskScopeRow> selectAffectedScopeSnapshot(@Param("scopeKeys") Set<String> scopeKeys, @Param("asOfDate") LocalDate asOfDate);
    List<String> selectScopesRequiringRuleVersion(@Param("ruleVersion") String ruleVersion);
    List<String> selectScopesRequiringDailyRefresh(@Param("asOfDate") LocalDate asOfDate);

    class RiskScopeRow {
        private Long inventoryBalanceId;
        private Long skuId;
        private Long salesPointId;
        private Long forecastId;
        private String skuCode;
        private String salesPointCode;
        private java.math.BigDecimal onHandQty;
        private java.math.BigDecimal predictedQtyD7;
        private java.math.BigDecimal predictedQtyD14;
        private java.math.BigDecimal predictedQtyD30;
        private java.math.BigDecimal safetyStockQty;
        private LocalDate forecastBaseDate;
        private String lotId;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate saleStopDate;
        private LocalDate receivedDate;
        private java.math.BigDecimal lotQty;
        private String lotStatus;
        public Long getInventoryBalanceId() { return inventoryBalanceId; }
        public void setInventoryBalanceId(Long v) { inventoryBalanceId = v; }
        public Long getSkuId() { return skuId; }
        public void setSkuId(Long v) { skuId = v; }
        public Long getSalesPointId() { return salesPointId; }
        public void setSalesPointId(Long v) { salesPointId = v; }
        public Long getForecastId() { return forecastId; }
        public void setForecastId(Long v) { forecastId = v; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String v) { skuCode = v; }
        public String getSalesPointCode() { return salesPointCode; }
        public void setSalesPointCode(String v) { salesPointCode = v; }
        public java.math.BigDecimal getOnHandQty() { return onHandQty; }
        public void setOnHandQty(java.math.BigDecimal v) { onHandQty = v; }
        public java.math.BigDecimal getPredictedQtyD7() { return predictedQtyD7; }
        public void setPredictedQtyD7(java.math.BigDecimal v) { predictedQtyD7 = v; }
        public java.math.BigDecimal getPredictedQtyD14() { return predictedQtyD14; }
        public void setPredictedQtyD14(java.math.BigDecimal v) { predictedQtyD14 = v; }
        public java.math.BigDecimal getPredictedQtyD30() { return predictedQtyD30; }
        public void setPredictedQtyD30(java.math.BigDecimal v) { predictedQtyD30 = v; }
        public java.math.BigDecimal getSafetyStockQty() { return safetyStockQty; }
        public void setSafetyStockQty(java.math.BigDecimal v) { safetyStockQty = v; }
        public LocalDate getForecastBaseDate() { return forecastBaseDate; }
        public void setForecastBaseDate(LocalDate v) { forecastBaseDate = v; }
        public String getLotId() { return lotId; }
        public void setLotId(String v) { lotId = v; }
        public String getLotNumber() { return lotNumber; }
        public void setLotNumber(String v) { lotNumber = v; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate v) { expiryDate = v; }
        public LocalDate getSaleStopDate() { return saleStopDate; }
        public void setSaleStopDate(LocalDate v) { saleStopDate = v; }
        public LocalDate getReceivedDate() { return receivedDate; }
        public void setReceivedDate(LocalDate v) { receivedDate = v; }
        public java.math.BigDecimal getLotQty() { return lotQty; }
        public void setLotQty(java.math.BigDecimal v) { lotQty = v; }
        public String getLotStatus() { return lotStatus; }
        public void setLotStatus(String v) { lotStatus = v; }
    }
}
