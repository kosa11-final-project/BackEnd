package com.stockit.backend.feature.inventorysync;

import java.util.Comparator;
import java.util.List;

/** 모든 multi-source lock이 공유하는 고정 순서입니다. */
public final class InventorySyncSourceOrder {
    public static final List<String> TYPES = List.of("OFFLINE", "ECOMMERCE", "GREETING", "WAREHOUSE");
    public static final String VALIDATION_PATTERN = "OFFLINE|ECOMMERCE|GREETING|WAREHOUSE";
    public static final Comparator<String> COMPARATOR = Comparator.comparingInt(TYPES::indexOf);
    private InventorySyncSourceOrder() { }
}
