package com.stockit.backend.feature.dashboard.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RiskSalesPointVO {

    private Long salesPointId;
    private String salesPointCode;
    private String salesPointName;
    private String channelType;
    private String regionCode;
    private BigDecimal availableStock;
    private long riskSkuCount;
    private BigDecimal expectedDisposalQty;
    private BigDecimal nearExpiryStock;
}
