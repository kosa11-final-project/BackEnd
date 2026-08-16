package com.stockit.backend.feature.dashboard.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WarehouseInventoryVO {

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String regionCode;
    private String address;
    private BigDecimal currentStock;
    private BigDecimal availableStock;
    private BigDecimal nearExpiryStock;
    private BigDecimal outboundStock;
    private long riskSkuCount;
}
