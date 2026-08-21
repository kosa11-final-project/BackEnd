package com.stockit.backend.feature.salesdaily.batch;

import static com.stockit.backend.feature.salesdaily.batch.SalesDailyCsvExportFileManager.TEMPORARY_PATH_CONTEXT_KEY;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableConfigurationProperties(SalesDailyCsvExportProperties.class)
public class SalesDailyCsvExportBatchConfiguration {

    public static final String JOB_NAME = "salesDailyCsvExportJob";
    public static final String BASE_DATE_PARAMETER = "baseDate";
    public static final String EXPORT_MODE_PARAMETER = "exportMode";
    public static final String CSV_HEADER = "sales_date,sku_id,sales_point_id,net_sales_qty";
    public static final String BLOB_NAME_CONTEXT_KEY = "salesDailyCsvBlobName";
    public static final String BLOB_URL_CONTEXT_KEY = "salesDailyCsvBlobUrl";
    public static final String EXPECTED_ROW_COUNT_CONTEXT_KEY =
            "salesDailyCsvExpectedRowCount";

    private static final int CHUNK_SIZE = 10_000;
    private static final String SELECT_BOOTSTRAP_ORACLE_SQL = """
            WITH
            params AS (
                SELECT CAST(? AS DATE) AS base_date
                FROM dual
            ),
            bounds AS (
                SELECT
                    MIN(daily.sales_date) AS first_sales_date,
                    params.base_date
                FROM sales_daily daily
                CROSS JOIN params
                WHERE daily.is_deleted = 0
                  AND daily.sales_date <= params.base_date
                GROUP BY params.base_date
            ),
            combinations AS (
                SELECT DISTINCT
                    daily.sku_id,
                    daily.sales_point_id
                FROM sales_daily daily
                CROSS JOIN params
                WHERE daily.is_deleted = 0
                  AND daily.sales_date <= params.base_date
            ),
            calendar AS (
                SELECT bounds.first_sales_date + LEVEL - 1 AS sales_date
                FROM bounds
                CONNECT BY LEVEL <= bounds.base_date - bounds.first_sales_date + 1
            )
            SELECT
                calendar.sales_date,
                combinations.sku_id,
                combinations.sales_point_id,
                COALESCE(daily.net_sales_qty, 0) AS net_sales_qty
            FROM combinations
            CROSS JOIN calendar
            LEFT JOIN sales_daily daily
              ON daily.sku_id = combinations.sku_id
             AND daily.sales_point_id = combinations.sales_point_id
             AND daily.sales_date = calendar.sales_date
             AND daily.is_deleted = 0
            ORDER BY
                combinations.sku_id,
                combinations.sales_point_id,
                calendar.sales_date
            """;
    private static final String SELECT_BOOTSTRAP_H2_SQL = """
            WITH RECURSIVE
            params(base_date) AS (
                SELECT CAST(? AS DATE)
                FROM dual
            ),
            bounds(first_sales_date, base_date) AS (
                SELECT
                    CAST(MIN(daily.sales_date) AS DATE),
                    CAST(params.base_date AS DATE)
                FROM sales_daily daily
                CROSS JOIN params
                WHERE daily.is_deleted = 0
                  AND daily.sales_date <= params.base_date
                GROUP BY params.base_date
            ),
            combinations(sku_id, sales_point_id) AS (
                SELECT DISTINCT
                    daily.sku_id,
                    daily.sales_point_id
                FROM sales_daily daily
                CROSS JOIN params
                WHERE daily.is_deleted = 0
                  AND daily.sales_date <= params.base_date
            ),
            calendar(sales_date, base_date) AS (
                SELECT
                    CAST(bounds.first_sales_date AS DATE),
                    CAST(bounds.base_date AS DATE)
                FROM bounds
                UNION ALL
                SELECT
                    CAST(calendar.sales_date AS DATE) + INTERVAL '1' DAY,
                    CAST(calendar.base_date AS DATE)
                FROM calendar
                WHERE CAST(calendar.sales_date AS DATE) < CAST(calendar.base_date AS DATE)
            )
            SELECT
                CAST(calendar.sales_date AS DATE) AS sales_date,
                combinations.sku_id,
                combinations.sales_point_id,
                COALESCE(daily.net_sales_qty, 0) AS net_sales_qty
            FROM combinations
            CROSS JOIN calendar
            LEFT JOIN sales_daily daily
              ON daily.sku_id = combinations.sku_id
             AND daily.sales_point_id = combinations.sales_point_id
             AND daily.sales_date = CAST(calendar.sales_date AS DATE)
             AND daily.is_deleted = 0
            ORDER BY
                combinations.sku_id,
                combinations.sales_point_id,
                calendar.sales_date
            """;
    private static final String SELECT_DAILY_SQL = """
            WITH
            params AS (
                SELECT CAST(? AS DATE) AS base_date
                FROM dual
            ),
            combinations AS (
                SELECT DISTINCT
                    daily.sku_id,
                    daily.sales_point_id
                FROM sales_daily daily
                CROSS JOIN params
                WHERE daily.is_deleted = 0
                  AND daily.sales_date <= params.base_date
            )
            SELECT
                params.base_date AS sales_date,
                combinations.sku_id,
                combinations.sales_point_id,
                COALESCE(daily.net_sales_qty, 0) AS net_sales_qty
            FROM combinations
            CROSS JOIN params
            LEFT JOIN sales_daily daily
              ON daily.sku_id = combinations.sku_id
             AND daily.sales_point_id = combinations.sales_point_id
             AND daily.sales_date = params.base_date
             AND daily.is_deleted = 0
            ORDER BY
                combinations.sku_id,
                combinations.sales_point_id,
                params.base_date
            """;
    private static final String SELECT_PANEL_STATS_SQL = """
            SELECT
                MIN(combination.first_sales_date) AS first_sales_date,
                COUNT(*) AS combination_count
            FROM (
                SELECT
                    daily.sku_id,
                    daily.sales_point_id,
                    MIN(daily.sales_date) AS first_sales_date
                FROM sales_daily daily
                WHERE daily.is_deleted = 0
                  AND daily.sales_date <= ?
                GROUP BY
                    daily.sku_id,
                    daily.sales_point_id
            ) combination
            """;

    @Bean
    public Job salesDailyCsvExportJob(
            JobRepository jobRepository,
            @Qualifier("salesDailyCsvExportValidationStep") Step validationStep,
            @Qualifier("salesDailyCsvExportStep") Step exportStep,
            @Qualifier("salesDailyCsvExportCompletenessValidationStep")
            Step completenessValidationStep,
            @Qualifier("salesDailyCsvUploadStep") Step uploadStep,
            SalesDailyCsvExportFileManager fileManager,
            JobParametersValidator salesDailyCsvExportJobParametersValidator
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(salesDailyCsvExportJobParametersValidator)
                .listener(fileManager)
                .start(validationStep)
                .next(exportStep)
                .next(completenessValidationStep)
                .next(uploadStep)
                .build();
    }

    @Bean
    public JobParametersValidator salesDailyCsvExportJobParametersValidator() {
        return parameters -> {
            String baseDate = parameters.getString(BASE_DATE_PARAMETER);
            try {
                parseBaseDate(baseDate);
                SalesDailyExportMode.from(parameters.getString(EXPORT_MODE_PARAMETER));
            } catch (IllegalArgumentException exception) {
                throw new JobParametersInvalidException(exception.getMessage());
            }
        };
    }

    @Bean
    public SalesDailyCsvExportFileManager salesDailyCsvExportFileManager(
            SalesDailyCsvExportProperties properties
    ) {
        return new SalesDailyCsvExportFileManager(properties);
    }

    @Bean
    public Step salesDailyCsvExportValidationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate
    ) {
        return new StepBuilder("salesDailyCsvExportValidationStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String value = (String) chunkContext.getStepContext()
                            .getJobParameters()
                            .get(BASE_DATE_PARAMETER);
                    LocalDate baseDate = parseBaseDate(value);
                    SalesDailyExportMode exportMode = SalesDailyExportMode.from(
                            (String) chunkContext.getStepContext()
                                    .getJobParameters()
                                    .get(EXPORT_MODE_PARAMETER)
                    );
                    PanelStats panelStats = jdbcTemplate.query(
                            SELECT_PANEL_STATS_SQL,
                            statement -> statement.setDate(1, Date.valueOf(baseDate)),
                            resultSet -> {
                                resultSet.next();
                                Date firstSalesDate = resultSet.getDate("first_sales_date");
                                return new PanelStats(
                                        firstSalesDate == null
                                                ? null
                                                : firstSalesDate.toLocalDate(),
                                        resultSet.getLong("combination_count")
                                );
                            }
                    );
                    if (panelStats.combinationCount() == 0L) {
                        throw new IllegalStateException(
                                noDataMessage(exportMode)
                        );
                    }
                    long dateCount = exportMode == SalesDailyExportMode.BOOTSTRAP
                            ? ChronoUnit.DAYS.between(
                                    panelStats.firstSalesDate(),
                                    baseDate
                            ) + 1L
                            : 1L;
                    long expectedRowCount = Math.multiplyExact(
                            panelStats.combinationCount(),
                            dateCount
                    );
                    chunkContext.getStepContext()
                            .getStepExecution()
                            .getJobExecution()
                            .getExecutionContext()
                            .putLong(EXPECTED_ROW_COUNT_CONTEXT_KEY, expectedRowCount);
                    return null;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step salesDailyCsvExportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("salesDailyCsvExportReader")
            JdbcCursorItemReader<SalesDailyCsvExportRow> reader,
            @Qualifier("salesDailyCsvExportWriter")
            FlatFileItemWriter<SalesDailyCsvExportRow> writer
    ) {
        return new StepBuilder("salesDailyCsvExportStep", jobRepository)
                .<SalesDailyCsvExportRow, SalesDailyCsvExportRow>chunk(
                        CHUNK_SIZE,
                        transactionManager
                )
                .reader(reader)
                .writer(writer)
                .build();
    }

    @Bean
    public Step salesDailyCsvExportCompletenessValidationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder(
                "salesDailyCsvExportCompletenessValidationStep",
                jobRepository
        )
                .tasklet((contribution, chunkContext) -> {
                    JobExecution jobExecution = chunkContext.getStepContext()
                            .getStepExecution()
                            .getJobExecution();
                    long expectedRowCount = jobExecution.getExecutionContext()
                            .getLong(EXPECTED_ROW_COUNT_CONTEXT_KEY);
                    long writeCount = jobExecution.getStepExecutions().stream()
                            .filter(step -> "salesDailyCsvExportStep".equals(step.getStepName()))
                            .mapToLong(StepExecution::getWriteCount)
                            .sum();
                    if (writeCount != expectedRowCount) {
                        throw new IllegalStateException(
                                "SALES_DAILY 완전 패널 행 수가 일치하지 않습니다. expected="
                                        + expectedRowCount + ", actual=" + writeCount
                        );
                    }
                    return null;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step salesDailyCsvUploadStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SalesDailyCsvUploader uploader
    ) {
        return new StepBuilder("salesDailyCsvUploadStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    JobExecution jobExecution = chunkContext.getStepContext()
                            .getStepExecution()
                            .getJobExecution();
                    LocalDate baseDate = parseBaseDate(
                            jobExecution.getJobParameters().getString(BASE_DATE_PARAMETER)
                    );
                    SalesDailyExportMode exportMode = SalesDailyExportMode.from(
                            jobExecution.getJobParameters().getString(EXPORT_MODE_PARAMETER)
                    );
                    Path temporaryPath = Path.of(
                            jobExecution.getExecutionContext().getString(
                                    TEMPORARY_PATH_CONTEXT_KEY
                            )
                    );
                    SalesDailyCsvUploadResult result = uploader.upload(
                            new SalesDailyCsvUploadRequest(
                                    exportMode,
                                    baseDate,
                                    temporaryPath
                            )
                    );
                    if (result != null && result.blobName() != null) {
                        jobExecution.getExecutionContext().putString(
                                BLOB_NAME_CONTEXT_KEY,
                                result.blobName()
                        );
                    }
                    if (result != null && result.blobUrl() != null) {
                        jobExecution.getExecutionContext().putString(
                                BLOB_URL_CONTEXT_KEY,
                                result.blobUrl()
                        );
                    }
                    jobRepository.updateExecutionContext(jobExecution);
                    return null;
                }, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<SalesDailyCsvExportRow> salesDailyCsvExportReader(
            DataSource dataSource,
            @Value("#{jobParameters['baseDate']}") String baseDateValue,
            @Value("#{jobParameters['exportMode'] ?: 'DAILY'}") String exportModeValue
    ) {
        LocalDate baseDate = parseBaseDate(baseDateValue);
        SalesDailyExportMode exportMode = SalesDailyExportMode.from(exportModeValue);
        return new JdbcCursorItemReaderBuilder<SalesDailyCsvExportRow>()
                .name("salesDailyCsvExportReader")
                .dataSource(dataSource)
                .sql(selectSql(exportMode, databaseProductName(dataSource)))
                .preparedStatementSetter(statement ->
                        statement.setDate(1, Date.valueOf(baseDate)))
                .rowMapper((resultSet, rowNumber) -> new SalesDailyCsvExportRow(
                        resultSet.getDate("sales_date").toLocalDate(),
                        resultSet.getLong("sku_id"),
                        resultSet.getLong("sales_point_id"),
                        resultSet.getBigDecimal("net_sales_qty")
                ))
                .fetchSize(CHUNK_SIZE)
                .saveState(false)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<SalesDailyCsvExportRow> salesDailyCsvExportWriter(
            @Value("#{jobExecutionContext['" + TEMPORARY_PATH_CONTEXT_KEY + "']}")
            String temporaryPath
    ) {
        return new FlatFileItemWriterBuilder<SalesDailyCsvExportRow>()
                .name("salesDailyCsvExportWriter")
                .resource(new FileSystemResource(temporaryPath))
                .encoding(StandardCharsets.UTF_8.name())
                .headerCallback(writer -> writer.write(CSV_HEADER))
                .lineAggregator(row -> String.join(",",
                        row.salesDate().toString(),
                        row.skuId().toString(),
                        row.salesPointId().toString(),
                        row.netSalesQty().stripTrailingZeros().toPlainString()
                ))
                .shouldDeleteIfExists(true)
                .shouldDeleteIfEmpty(true)
                .saveState(false)
                .build();
    }

    private static LocalDate parseBaseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("baseDate Job Parameter는 필수입니다.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "baseDate Job Parameter는 yyyy-MM-dd 형식이어야 합니다.",
                    exception
            );
        }
    }

    private static String selectSql(
            SalesDailyExportMode exportMode,
            String databaseProductName
    ) {
        if (exportMode == SalesDailyExportMode.DAILY) {
            return SELECT_DAILY_SQL;
        }
        return "H2".equalsIgnoreCase(databaseProductName)
                ? SELECT_BOOTSTRAP_H2_SQL
                : SELECT_BOOTSTRAP_ORACLE_SQL;
    }

    private static String noDataMessage(SalesDailyExportMode exportMode) {
        return exportMode == SalesDailyExportMode.BOOTSTRAP
                ? "기준일 이전의 유효한 SALES_DAILY 데이터가 없습니다."
                : "기준일까지 유효한 SALES_DAILY 조합이 없습니다.";
    }

    private static String databaseProductName(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException exception) {
            throw new IllegalStateException("데이터베이스 종류를 확인하지 못했습니다.", exception);
        }
    }

    private record PanelStats(LocalDate firstSalesDate, long combinationCount) {
    }
}
