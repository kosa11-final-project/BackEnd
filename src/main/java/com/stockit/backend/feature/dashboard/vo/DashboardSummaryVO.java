package com.stockit.backend.feature.dashboard.vo;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardSummaryVO {

    private BigDecimal totalCurrentStock;
    private BigDecimal totalAvailableStock;
    private long criticalSkuCount;
    private long warningSkuCount;
    private long shortageSkuCount;
    private BigDecimal expectedDisposalQty;
}
