package com.stockit.backend.feature.salesdaily.batch;

public record SalesDailyCsvUploadResult(String blobName, String blobUrl) {

    public static SalesDailyCsvUploadResult local() {
        return new SalesDailyCsvUploadResult(null, null);
    }
}
