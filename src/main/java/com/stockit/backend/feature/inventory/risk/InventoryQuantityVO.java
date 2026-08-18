package com.stockit.backend.feature.inventory.risk;

import java.math.BigDecimal;

/** 판매처 귀속 기준으로 합산한 가용 수량입니다. */
public class InventoryQuantityVO {

    private BigDecimal onHandQty;

    public BigDecimal getOnHandQty() {
        return onHandQty;
    }

    public void setOnHandQty(BigDecimal onHandQty) {
        this.onHandQty = onHandQty;
    }

}
