package com.stockit.backend.feature.inventory.vo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Date;

public class InventoryItemVO {

    private String productCode;
    private String productName;
    private String skuCode;
    private String skuName;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private Integer categoryLevel;
    private Long parentCategoryId;
    private String parentCategoryName;
    private Integer parentCategoryLevel;
    private Long grandparentCategoryId;
    private String grandparentCategoryName;
    private Integer grandparentCategoryLevel;
    private String channelType;
    private String salesPointCode;
    private String salesPointName;
    private String storageType;
    private BigDecimal sellingPrice;
    private BigDecimal currentQty;
    private BigDecimal availableQty;
    private BigDecimal reservedQty;
    private BigDecimal safetyQty;
    private String inventoryFactState;
    private String riskGrade;
    private String assessmentStatus;
    private String riskReason;
    private String locationsJson;
    private Integer locationCount;
    private String salesPointsJson;
    private Integer ownerSalesPointCount;
    private Integer lotCount;
    private Integer nearestExpiryDays;
    private Date nearestExpiryDate;
    private BigDecimal dailySales;
    private BigDecimal forecast14Days;
    private Timestamp updatedAt;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Integer getCategoryLevel() { return categoryLevel; }
    public void setCategoryLevel(Integer categoryLevel) { this.categoryLevel = categoryLevel; }
    public Long getParentCategoryId() { return parentCategoryId; }
    public void setParentCategoryId(Long parentCategoryId) { this.parentCategoryId = parentCategoryId; }
    public String getParentCategoryName() { return parentCategoryName; }
    public void setParentCategoryName(String parentCategoryName) { this.parentCategoryName = parentCategoryName; }
    public Integer getParentCategoryLevel() { return parentCategoryLevel; }
    public void setParentCategoryLevel(Integer parentCategoryLevel) { this.parentCategoryLevel = parentCategoryLevel; }
    public Long getGrandparentCategoryId() { return grandparentCategoryId; }
    public void setGrandparentCategoryId(Long grandparentCategoryId) { this.grandparentCategoryId = grandparentCategoryId; }
    public String getGrandparentCategoryName() { return grandparentCategoryName; }
    public void setGrandparentCategoryName(String grandparentCategoryName) { this.grandparentCategoryName = grandparentCategoryName; }
    public Integer getGrandparentCategoryLevel() { return grandparentCategoryLevel; }
    public void setGrandparentCategoryLevel(Integer grandparentCategoryLevel) { this.grandparentCategoryLevel = grandparentCategoryLevel; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public String getSalesPointCode() { return salesPointCode; }
    public void setSalesPointCode(String salesPointCode) { this.salesPointCode = salesPointCode; }
    public String getSalesPointName() { return salesPointName; }
    public void setSalesPointName(String salesPointName) { this.salesPointName = salesPointName; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    public BigDecimal getCurrentQty() { return currentQty; }
    public void setCurrentQty(BigDecimal currentQty) { this.currentQty = currentQty; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public void setReservedQty(BigDecimal reservedQty) { this.reservedQty = reservedQty; }
    public BigDecimal getSafetyQty() { return safetyQty; }
    public void setSafetyQty(BigDecimal safetyQty) { this.safetyQty = safetyQty; }
    public String getInventoryFactState() { return inventoryFactState; }
    public void setInventoryFactState(String inventoryFactState) { this.inventoryFactState = inventoryFactState; }
    public String getRiskGrade() { return riskGrade; }
    public void setRiskGrade(String riskGrade) { this.riskGrade = riskGrade; }
    public String getAssessmentStatus() { return assessmentStatus; }
    public void setAssessmentStatus(String assessmentStatus) { this.assessmentStatus = assessmentStatus; }
    public String getRiskReason() { return riskReason; }
    public void setRiskReason(String riskReason) { this.riskReason = riskReason; }
    public String getLocationsJson() { return locationsJson; }
    public void setLocationsJson(String locationsJson) { this.locationsJson = locationsJson; }
    public Integer getLocationCount() { return locationCount; }
    public void setLocationCount(Integer locationCount) { this.locationCount = locationCount; }
    public String getSalesPointsJson() { return salesPointsJson; }
    public void setSalesPointsJson(String salesPointsJson) { this.salesPointsJson = salesPointsJson; }
    public Integer getOwnerSalesPointCount() { return ownerSalesPointCount; }
    public void setOwnerSalesPointCount(Integer ownerSalesPointCount) { this.ownerSalesPointCount = ownerSalesPointCount; }
    public Integer getLotCount() { return lotCount; }
    public void setLotCount(Integer lotCount) { this.lotCount = lotCount; }
    public Integer getNearestExpiryDays() { return nearestExpiryDays; }
    public void setNearestExpiryDays(Integer nearestExpiryDays) { this.nearestExpiryDays = nearestExpiryDays; }
    public Date getNearestExpiryDate() { return nearestExpiryDate; }
    public void setNearestExpiryDate(Date nearestExpiryDate) { this.nearestExpiryDate = nearestExpiryDate; }
    public BigDecimal getDailySales() { return dailySales; }
    public void setDailySales(BigDecimal dailySales) { this.dailySales = dailySales; }
    public BigDecimal getForecast14Days() { return forecast14Days; }
    public void setForecast14Days(BigDecimal forecast14Days) { this.forecast14Days = forecast14Days; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
