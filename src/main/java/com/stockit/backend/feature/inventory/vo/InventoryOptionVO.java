package com.stockit.backend.feature.inventory.vo;

import java.math.BigDecimal;

/**
 * MyBatis가 기준정보와 현재 재고 집계를 공통 형태로 읽기 위한 내부 VO입니다.
 * API 응답에서는 옵션 종류별로 의미가 있는 필드만 노출합니다.
 */
public class InventoryOptionVO {

    private String code;
    private String name;
    private String parentCode;
    private String regionCode;
    private String channelType;
    private String availability;
    private Long currentSkuCount;
    private Long currentBalanceRowCount;
    private BigDecimal currentOnHandQty;
    private Integer categoryLevel;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public Long getCurrentSkuCount() { return currentSkuCount; }
    public void setCurrentSkuCount(Long currentSkuCount) { this.currentSkuCount = currentSkuCount; }
    public Long getCurrentBalanceRowCount() { return currentBalanceRowCount; }
    public void setCurrentBalanceRowCount(Long currentBalanceRowCount) {
        this.currentBalanceRowCount = currentBalanceRowCount;
    }
    public BigDecimal getCurrentOnHandQty() { return currentOnHandQty; }
    public void setCurrentOnHandQty(BigDecimal currentOnHandQty) { this.currentOnHandQty = currentOnHandQty; }
    public Integer getCategoryLevel() { return categoryLevel; }
    public void setCategoryLevel(Integer categoryLevel) { this.categoryLevel = categoryLevel; }
}
