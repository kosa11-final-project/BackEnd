package com.stockit.backend.feature.inventorysync;

/** Inventory sync endpoint paths shared by controllers and request guards. */
public final class InventorySyncRoutes {
    public static final String RUNS = "/api/v1/inventory-sync-runs";
    public static final String DEMO_ADJUSTMENTS = "/api/v1/inventory-source-demo/adjustments";

    private InventorySyncRoutes() {
    }
}
