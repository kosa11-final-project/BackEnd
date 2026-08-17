package com.stockit.backend.feature.salesdaily.batch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SalesDailyBlobProperties.class)
public class SalesDailyCsvUploadConfiguration {

    @Bean
    public SalesDailyBlobNameFactory salesDailyBlobNameFactory() {
        return new SalesDailyBlobNameFactory();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.sales-daily-export",
            name = "destination",
            havingValue = "LOCAL",
            matchIfMissing = true
    )
    public SalesDailyCsvUploader localSalesDailyCsvUploader() {
        return new LocalSalesDailyCsvUploader();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.sales-daily-export",
            name = "destination",
            havingValue = "AZURE_BLOB"
    )
    public SalesDailyCsvUploader azureBlobSalesDailyCsvUploader(
            SalesDailyBlobProperties properties,
            SalesDailyBlobNameFactory blobNameFactory
    ) {
        return new AzureBlobSalesDailyCsvUploader(properties, blobNameFactory);
    }
}
