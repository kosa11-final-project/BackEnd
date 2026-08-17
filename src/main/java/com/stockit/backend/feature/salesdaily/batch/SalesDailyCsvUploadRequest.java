package com.stockit.backend.feature.salesdaily.batch;

import java.nio.file.Path;
import java.time.LocalDate;

public record SalesDailyCsvUploadRequest(
        SalesDailyExportMode exportMode,
        LocalDate baseDate,
        Path temporaryPath
) {
}
