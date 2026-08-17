package com.stockit.backend.feature.salesdaily.batch;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "classpath:sales-daily-export-test-schema.sql")
class SalesDailyCsvExportBatchTest {

    private static final Path OUTPUT_DIRECTORY = createOutputDirectory();
    private static final Path OUTPUT_PATH = OUTPUT_DIRECTORY.resolve("sales_daily.csv");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureOutputPath(DynamicPropertyRegistry registry) {
        registry.add("app.sales-daily-export.output-path", OUTPUT_PATH::toString);
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
    void bootstrapExportsOnlyActiveRowsUpToBaseDateInRequiredColumnOrder() throws Exception {
        insertSalesDaily(102L, 20L, LocalDate.of(2026, 7, 31), "8.000", 0);
        insertSalesDaily(101L, 10L, LocalDate.of(2026, 7, 30), "12.300", 0);
        insertSalesDaily(101L, 10L, LocalDate.of(2026, 8, 1), "99.000", 0);
        insertSalesDaily(103L, 30L, LocalDate.of(2026, 7, 29), "77.000", 1);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                jobParameters("2026-07-31", SalesDailyExportMode.BOOTSTRAP)
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.readAllLines(OUTPUT_PATH, StandardCharsets.UTF_8))
                .containsExactly(
                        "sales_date,sku_id,sales_point_id,net_sales_qty",
                        "2026-07-30,101,10,12.3",
                        "2026-07-31,102,20,8"
                );
        assertThat(temporaryFiles()).isEmpty();
    }

    @Test
    void dailyExportIncludesOnlyTheRequestedSalesDate() throws Exception {
        insertSalesDaily(101L, 10L, LocalDate.of(2026, 8, 17), "7.000", 0);
        insertSalesDaily(101L, 10L, LocalDate.of(2026, 8, 18), "12.300", 0);
        insertSalesDaily(102L, 20L, LocalDate.of(2026, 8, 18), "8.000", 0);
        insertSalesDaily(103L, 30L, LocalDate.of(2026, 8, 18), "77.000", 1);
        insertSalesDaily(104L, 40L, LocalDate.of(2026, 8, 19), "9.000", 0);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                jobParameters("2026-08-18", SalesDailyExportMode.DAILY)
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.readAllLines(OUTPUT_PATH, StandardCharsets.UTF_8))
                .containsExactly(
                        "sales_date,sku_id,sales_point_id,net_sales_qty",
                        "2026-08-18,101,10,12.3",
                        "2026-08-18,102,20,8"
                );
    }

    @Test
    void defaultsToDailyExportWhenExportModeIsOmitted() throws Exception {
        insertSalesDaily(101L, 10L, LocalDate.of(2026, 8, 17), "7.000", 0);
        insertSalesDaily(102L, 20L, LocalDate.of(2026, 8, 18), "8.000", 0);

        JobParameters parameters = new JobParametersBuilder()
                .addString(
                        SalesDailyCsvExportBatchConfiguration.BASE_DATE_PARAMETER,
                        "2026-08-18"
                )
                .addLong("run.id", System.nanoTime())
                .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(Files.readAllLines(OUTPUT_PATH, StandardCharsets.UTF_8))
                .containsExactly(
                        "sales_date,sku_id,sales_point_id,net_sales_qty",
                        "2026-08-18,102,20,8"
                );
    }

    @Test
    void failsWithoutCreatingFinalFileWhenNoRowsMatch() throws Exception {
        insertSalesDaily(101L, 10L, LocalDate.of(2026, 8, 1), "10.000", 0);
        insertSalesDaily(102L, 20L, LocalDate.of(2026, 7, 31), "20.000", 1);

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters("2026-07-31"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(OUTPUT_PATH).doesNotExist();
        assertThat(temporaryFiles()).isEmpty();
    }

    @Test
    void preservesPreviousFinalFileAndDeletesTemporaryFileWhenWritingFails() throws Exception {
        Files.writeString(OUTPUT_PATH, "previous-complete-file", StandardCharsets.UTF_8);
        insertSalesDaily(101L, 10L, LocalDate.of(2026, 7, 31), null, 0);

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters("2026-07-31"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(Files.readString(OUTPUT_PATH, StandardCharsets.UTF_8))
                .isEqualTo("previous-complete-file");
        assertThat(temporaryFiles()).isEmpty();
    }

    private void insertSalesDaily(
            Long skuId,
            Long salesPointId,
            LocalDate salesDate,
            String netSalesQty,
            int isDeleted
    ) {
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
                skuId,
                salesPointId,
                Date.valueOf(salesDate),
                netSalesQty == null ? null : new BigDecimal(netSalesQty),
                isDeleted
        );
    }

    private static JobParameters jobParameters(String baseDate) {
        return jobParameters(baseDate, SalesDailyExportMode.DAILY);
    }

    private static JobParameters jobParameters(
            String baseDate,
            SalesDailyExportMode exportMode
    ) {
        return new JobParametersBuilder()
                .addString(SalesDailyCsvExportBatchConfiguration.BASE_DATE_PARAMETER, baseDate)
                .addString(
                        SalesDailyCsvExportBatchConfiguration.EXPORT_MODE_PARAMETER,
                        exportMode.name()
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
            return Files.createTempDirectory("sales-daily-export-test-");
        } catch (IOException exception) {
            throw new IllegalStateException("테스트 출력 경로를 생성하지 못했습니다.", exception);
        }
    }
}
