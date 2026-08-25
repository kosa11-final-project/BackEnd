package com.stockit.backend.feature.salesdaily.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "classpath:sales-daily-export-test-schema.sql")
class SalesDailyCsvAzureUploadBatchTest {

    private static final Path OUTPUT_DIRECTORY = createOutputDirectory();
    private static final Path OUTPUT_PATH = OUTPUT_DIRECTORY.resolve("sales_daily.csv");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobExplorer jobExplorer;

    @MockitoBean
    private SalesDailyCsvUploader uploader;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.sales-daily-export.output-path", OUTPUT_PATH::toString);
        registry.add("app.sales-daily-export.destination", () -> "AZURE_BLOB");
    }

    @BeforeEach
    void cleanOutputFiles() throws IOException {
        Files.deleteIfExists(OUTPUT_PATH);
        deleteTemporaryFiles();
    }

    @AfterAll
    static void deleteOutputDirectory() throws IOException {
        Files.deleteIfExists(OUTPUT_PATH);
        deleteTemporaryFiles();
        Files.deleteIfExists(OUTPUT_DIRECTORY);
    }

    @Test
    void uploadsTemporaryCsvAndRecordsBlobResult() throws Exception {
        insertSalesDaily(LocalDate.of(2026, 9, 4));
        when(uploader.upload(any())).thenReturn(new SalesDailyCsvUploadResult(
                "sales-daily/sales-date=2026-09-04/sales_daily.csv",
                "https://stockit.blob.core.windows.net/demand-forecast-input/blob.csv"
        ));

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters("2026-09-04"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        ArgumentCaptor<SalesDailyCsvUploadRequest> captor =
                ArgumentCaptor.forClass(SalesDailyCsvUploadRequest.class);
        verify(uploader).upload(captor.capture());
        SalesDailyCsvUploadRequest request = captor.getValue();
        assertThat(request.exportMode()).isEqualTo(SalesDailyExportMode.DAILY);
        assertThat(request.baseDate()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(request.temporaryPath()).isEqualTo(
                OUTPUT_PATH.resolveSibling(
                        "sales_daily.csv." + execution.getId() + ".tmp"
                ).toAbsolutePath().normalize()
        );
        assertThat(execution.getExecutionContext().getString(
                SalesDailyCsvExportBatchConfiguration.BLOB_NAME_CONTEXT_KEY
        )).isEqualTo("sales-daily/sales-date=2026-09-04/sales_daily.csv");
        assertThat(execution.getExecutionContext().getString(
                SalesDailyCsvExportBatchConfiguration.BLOB_URL_CONTEXT_KEY
        )).startsWith("https://stockit.blob.core.windows.net/");
        JobExecution persistedExecution = jobExplorer.getJobExecution(execution.getId());
        assertThat(persistedExecution).isNotNull();
        assertThat(persistedExecution.getExecutionContext().getString(
                SalesDailyCsvExportBatchConfiguration.BLOB_URL_CONTEXT_KEY
        )).startsWith("https://stockit.blob.core.windows.net/");
        assertThat(OUTPUT_PATH).exists();
        assertThat(temporaryFiles()).isEmpty();
    }

    @Test
    void failsJobAndPreservesPreviousFinalFileWhenUploadFails() throws Exception {
        Files.writeString(OUTPUT_PATH, "previous-complete-file", StandardCharsets.UTF_8);
        insertSalesDaily(LocalDate.of(2026, 9, 4));
        when(uploader.upload(any())).thenThrow(
                new IllegalStateException("Azure Blob upload failed")
        );

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters("2026-09-04"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(Files.readString(OUTPUT_PATH, StandardCharsets.UTF_8))
                .isEqualTo("previous-complete-file");
        assertThat(temporaryFiles()).isEmpty();
    }

    private void insertSalesDaily(LocalDate salesDate) {
        jdbcTemplate.update(
                """
                        INSERT INTO sales_daily (
                            sku_id,
                            sales_point_id,
                            sales_date,
                            net_sales_qty,
                            is_deleted
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                101L,
                10L,
                Date.valueOf(salesDate),
                new BigDecimal("12.3"),
                0
        );
    }

    private static JobParameters jobParameters(String baseDate) {
        return new JobParametersBuilder()
                .addString(SalesDailyCsvExportBatchConfiguration.BASE_DATE_PARAMETER, baseDate)
                .addString(
                        SalesDailyCsvExportBatchConfiguration.EXPORT_MODE_PARAMETER,
                        SalesDailyExportMode.DAILY.name()
                )
                .addLong("run.id", System.nanoTime())
                .toJobParameters();
    }

    private static List<Path> temporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(OUTPUT_DIRECTORY)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".tmp"))
                    .toList();
        }
    }

    private static void deleteTemporaryFiles() throws IOException {
        for (Path temporaryFile : temporaryFiles()) {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static Path createOutputDirectory() {
        try {
            return Files.createTempDirectory("sales-daily-azure-upload-test-");
        } catch (IOException exception) {
            throw new IllegalStateException("테스트 출력 경로를 생성하지 못했습니다.", exception);
        }
    }
}
