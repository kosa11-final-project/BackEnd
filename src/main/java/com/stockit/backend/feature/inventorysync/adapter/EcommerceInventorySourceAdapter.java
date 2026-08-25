package com.stockit.backend.feature.inventorysync.adapter;

import org.springframework.stereotype.Component;

@Component
public class EcommerceInventorySourceAdapter implements InventorySourceAdapter {
    @Override public String sourceType() { return "ECOMMERCE"; }
}
