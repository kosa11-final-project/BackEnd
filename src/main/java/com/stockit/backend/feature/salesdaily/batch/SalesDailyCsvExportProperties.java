package com.stockit.backend.feature.salesdaily.batch;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sales-daily-export")
public record SalesDailyCsvExportProperties(
        Path outputPath,
        SalesDailyExportDestination destination
) {

    public Path requiredOutputPath() {
        if (outputPath == null) {
            throw new IllegalStateException("SALES_DAILY CSV 출력 경로가 설정되지 않았습니다.");
        }
        return outputPath.toAbsolutePath().normalize();
    }

    public SalesDailyExportDestination requiredDestination() {
        return destination == null ? SalesDailyExportDestination.LOCAL : destination;
    }
}
