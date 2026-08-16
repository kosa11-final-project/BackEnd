package com.stockit.backend.feature.inventory.vo;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class InventorySummaryVO {

    private BigDecimal totalCurrentQty;
    private BigDecimal totalAvailableQty;
    private BigDecimal totalReservedQty;
    private long underSafetyCount;
    private long dangerRiskCount;
    private long cautionRiskCount;
    private long safeRiskCount;
    private Timestamp lastSyncTime;

    public BigDecimal getTotalCurrentQty() { return totalCurrentQty; }
    public void setTotalCurrentQty(BigDecimal value) { this.totalCurrentQty = value; }
    public BigDecimal getTotalAvailableQty() { return totalAvailableQty; }
    public void setTotalAvailableQty(BigDecimal value) { this.totalAvailableQty = value; }
    public BigDecimal getTotalReservedQty() { return totalReservedQty; }
    public void setTotalReservedQty(BigDecimal value) { this.totalReservedQty = value; }
    public long getUnderSafetyCount() { return underSafetyCount; }
    public void setUnderSafetyCount(long value) { this.underSafetyCount = value; }
    public long getDangerRiskCount() { return dangerRiskCount; }
    public void setDangerRiskCount(long value) { this.dangerRiskCount = value; }
    public long getCautionRiskCount() { return cautionRiskCount; }
    public void setCautionRiskCount(long value) { this.cautionRiskCount = value; }
    public long getSafeRiskCount() { return safeRiskCount; }
    public void setSafeRiskCount(long value) { this.safeRiskCount = value; }
    public Timestamp getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(Timestamp value) { this.lastSyncTime = value; }
}
