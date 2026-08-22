package com.stockit.backend.feature.inventorysync.adapter;

import org.springframework.stereotype.Component;

@Component
public class GreetingInventorySourceAdapter implements InventorySourceAdapter {
    @Override public String sourceType() { return "GREETING"; }
}
