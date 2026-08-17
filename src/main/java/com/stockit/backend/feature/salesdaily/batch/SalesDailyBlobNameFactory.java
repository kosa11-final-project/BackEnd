package com.stockit.backend.feature.salesdaily.batch;

public class SalesDailyBlobNameFactory {

    public String create(SalesDailyBlobProperties properties, SalesDailyCsvUploadRequest request) {
        if (request.exportMode() == SalesDailyExportMode.BOOTSTRAP) {
            return String.format(
                    "%s-bootstrap/base-date=%s/sales_daily.csv",
                    properties.normalizedBlobPrefix(),
                    request.baseDate()
            );
        }
        return String.format(
                "%s/sales-date=%s/sales_daily.csv",
                properties.normalizedBlobPrefix(),
                request.baseDate()
        );
    }
}
