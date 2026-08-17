package com.stockit.backend.feature.salesdaily.batch;

import java.util.Locale;

public enum SalesDailyExportMode {
    BOOTSTRAP,
    DAILY;

    public static SalesDailyExportMode from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "exportMode Job Parameter는 BOOTSTRAP 또는 DAILY여야 합니다.",
                    exception
            );
        }
    }
}
