package com.stockit.backend.feature.salesdaily.batch;

import java.nio.charset.StandardCharsets;

import com.azure.core.util.Context;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobUploadFromFileOptions;

public class AzureBlobSalesDailyCsvUploader implements SalesDailyCsvUploader {

    private final SalesDailyBlobProperties properties;
    private final SalesDailyBlobNameFactory blobNameFactory;

    public AzureBlobSalesDailyCsvUploader(
            SalesDailyBlobProperties properties,
            SalesDailyBlobNameFactory blobNameFactory
    ) {
        this.properties = properties;
        this.blobNameFactory = blobNameFactory;
    }

    @Override
    public SalesDailyCsvUploadResult upload(SalesDailyCsvUploadRequest request) {
        properties.validateForUpload();
        String blobName = blobNameFactory.create(properties, request);
        BlobClient blobClient = new BlobServiceClientBuilder()
                .endpoint(properties.accountUrl())
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient()
                .getBlobContainerClient(properties.containerName())
                .getBlobClient(blobName);

        BlobUploadFromFileOptions options = new BlobUploadFromFileOptions(
                request.temporaryPath().toString()
        ).setHeaders(new BlobHttpHeaders().setContentType(
                "text/csv; charset=" + StandardCharsets.UTF_8.name()
        ));
        // 요청 조건을 두지 않아 동일 날짜 partition은 최신 CSV로 덮어쓴다.
        blobClient.uploadFromFileWithResponse(
                options,
                null,
                Context.NONE
        );

        return new SalesDailyCsvUploadResult(blobName, blobClient.getBlobUrl());
    }
}
