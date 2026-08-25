package com.stockit.backend.feature.salesdaily.batch;

public interface SalesDailyCsvUploader {

    SalesDailyCsvUploadResult upload(SalesDailyCsvUploadRequest request);
}
