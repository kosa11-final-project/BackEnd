package com.stockit.backend.feature.salesdaily.batch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SalesDailyCsvUploadConfiguration {

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

}
