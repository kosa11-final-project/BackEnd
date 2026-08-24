package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;
import java.sql.Timestamp;

/** 재고 동기화 시 RISK_ASSESSMENT에 저장한 최신 판정 결과입니다. */
public class PersistedRiskAssessmentVO {

    private String dbRiskGrade;
    private String shortageYn;
    private BigDecimal stockDays;
    private Integer holdingDays;
    private Integer expiryDaysLeft;
    private String ruleVersion;
    private String reasonMessage;
    private Timestamp assessedAt;

    public String getDbRiskGrade() {
        return dbRiskGrade;
    }

    public void setDbRiskGrade(String dbRiskGrade) {
        this.dbRiskGrade = dbRiskGrade;
    }

    public String getShortageYn() {
        return shortageYn;
    }

    public void setShortageYn(String shortageYn) {
        this.shortageYn = shortageYn;
    }

    public BigDecimal getStockDays() {
        return stockDays;
    }

    public void setStockDays(BigDecimal stockDays) {
        this.stockDays = stockDays;
    }

    public Integer getHoldingDays() {
        return holdingDays;
    }

    public void setHoldingDays(Integer holdingDays) {
        this.holdingDays = holdingDays;
    }

    public Integer getExpiryDaysLeft() {
        return expiryDaysLeft;
    }

    public void setExpiryDaysLeft(Integer expiryDaysLeft) {
        this.expiryDaysLeft = expiryDaysLeft;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getReasonMessage() {
        return reasonMessage;
    }

    public void setReasonMessage(String reasonMessage) {
        this.reasonMessage = reasonMessage;
    }

    public Timestamp getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(Timestamp assessedAt) {
        this.assessedAt = assessedAt;
    }
}
