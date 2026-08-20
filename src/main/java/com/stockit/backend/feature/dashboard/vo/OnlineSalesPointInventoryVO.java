package com.stockit.backend.feature.dashboard.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnlineSalesPointInventoryVO {

    private Long salesPointId;
    private String salesPointCode;
    private String salesPointName;
    private String regionCode;
    private String address;
    private long storageWarehouseCount;
    private BigDecimal currentStock;
    private BigDecimal availableStock;
    private BigDecimal nearExpiryStock;
    private BigDecimal expectedDisposalQty;
    private long riskSkuCount;
}
