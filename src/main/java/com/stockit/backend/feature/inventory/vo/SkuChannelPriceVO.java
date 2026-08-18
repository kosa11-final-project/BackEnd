package com.stockit.backend.feature.inventory.vo;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

public class SkuChannelPriceVO {

    private Long skuChannelPriceId;
    private Long skuId;
    private String skuCode;
    private Long salesPointId;
    private String salesPointCode;
    private String salesPointName;
    private BigDecimal sellingPrice;
    private BigDecimal actualPrice;
    private BigDecimal minimumSellingPrice;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String priceStatus;
    private Timestamp updatedAt;

    public Long getSkuChannelPriceId() { return skuChannelPriceId; }
    public void setSkuChannelPriceId(Long skuChannelPriceId) { this.skuChannelPriceId = skuChannelPriceId; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public Long getSalesPointId() { return salesPointId; }
    public void setSalesPointId(Long salesPointId) { this.salesPointId = salesPointId; }

    public String getSalesPointCode() { return salesPointCode; }
    public void setSalesPointCode(String salesPointCode) { this.salesPointCode = salesPointCode; }

    public String getSalesPointName() { return salesPointName; }
    public void setSalesPointName(String salesPointName) { this.salesPointName = salesPointName; }

    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }

    public BigDecimal getActualPrice() { return actualPrice; }
    public void setActualPrice(BigDecimal actualPrice) { this.actualPrice = actualPrice; }

    public BigDecimal getMinimumSellingPrice() { return minimumSellingPrice; }
    public void setMinimumSellingPrice(BigDecimal minimumSellingPrice) { this.minimumSellingPrice = minimumSellingPrice; }

    public Date getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Date effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public Date getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(Date effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getPriceStatus() { return priceStatus; }
    public void setPriceStatus(String priceStatus) { this.priceStatus = priceStatus; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
