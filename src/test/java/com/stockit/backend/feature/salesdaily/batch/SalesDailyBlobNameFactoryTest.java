package com.stockit.backend.feature.salesdaily.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SalesDailyBlobNameFactoryTest {

    private final SalesDailyBlobNameFactory factory = new SalesDailyBlobNameFactory();

    @Test
    void createsDailyPartitionBlobName() {
        SalesDailyBlobProperties properties = new SalesDailyBlobProperties(
                "https://stockit.blob.core.windows.net",
                "demand-forecast-input",
                "/sales-daily/"
        );
        SalesDailyCsvUploadRequest request = new SalesDailyCsvUploadRequest(
                SalesDailyExportMode.DAILY,
                LocalDate.of(2026, 9, 4),
                Path.of("sales_daily.csv.tmp")
        );

        assertThat(factory.create(properties, request)).isEqualTo(
                "sales-daily/sales-date=2026-09-04/sales_daily.csv"
        );
    }

    @Test
    void createsBootstrapBlobName() {
        SalesDailyBlobProperties properties = new SalesDailyBlobProperties(
                "https://stockit.blob.core.windows.net",
                "demand-forecast-input",
                "sales-daily"
        );
        SalesDailyCsvUploadRequest request = new SalesDailyCsvUploadRequest(
                SalesDailyExportMode.BOOTSTRAP,
                LocalDate.of(2026, 8, 17),
                Path.of("sales_daily.csv.tmp")
        );

        assertThat(factory.create(properties, request)).isEqualTo(
                "sales-daily-bootstrap/base-date=2026-08-17/sales_daily.csv"
        );
    }

    @Test
    void reportsMissingAzureConfigurationOnlyWhenValidated() {
        SalesDailyBlobProperties properties = new SalesDailyBlobProperties(
                "",
                "",
                "sales-daily"
        );

        assertThatThrownBy(properties::validateForUpload)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AZURE_STORAGE_ACCOUNT_URL");
    }

    @Test
    void rejectsNonHttpsAccountUrl() {
        SalesDailyBlobProperties properties = new SalesDailyBlobProperties(
                "http://stockit.blob.core.windows.net",
                "demand-forecast-input",
                "sales-daily"
        );

        assertThatThrownBy(properties::validateForUpload)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS URL");
    }
}
