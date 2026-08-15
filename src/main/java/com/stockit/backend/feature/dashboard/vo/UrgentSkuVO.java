package com.stockit.backend.feature.dashboard.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UrgentSkuVO {

    private Long skuId;
    private String skuCode;
    private String skuName;
    private String stockLocationType;
    private Long stockLocationId;
    private String stockLocationCode;
    private String stockLocationName;
    private Long allocatedSalesPointId;
    private String allocatedSalesPointName;
    private Integer expiryDaysLeft;
    private Integer saleStopDaysLeft;
    private BigDecimal expectedDisposalQty;
    private String reasonMessage;
}
