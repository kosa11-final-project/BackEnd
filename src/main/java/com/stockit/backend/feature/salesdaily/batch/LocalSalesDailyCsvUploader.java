package com.stockit.backend.feature.salesdaily.batch;

public class LocalSalesDailyCsvUploader implements SalesDailyCsvUploader {

    @Override
    public SalesDailyCsvUploadResult upload(SalesDailyCsvUploadRequest request) {
        return SalesDailyCsvUploadResult.local();
    }
}
