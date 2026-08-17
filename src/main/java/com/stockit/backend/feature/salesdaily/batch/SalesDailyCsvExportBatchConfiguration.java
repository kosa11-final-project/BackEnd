package com.stockit.backend.feature.salesdaily.batch;

import static com.stockit.backend.feature.salesdaily.batch.SalesDailyCsvExportFileManager.TEMPORARY_PATH_CONTEXT_KEY;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
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
import org.springframework.jdbc.core.ResultSetExtractor;
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

    private static final int CHUNK_SIZE = 1_000;
    private static final String SELECT_BOOTSTRAP_SQL = """
            SELECT
                daily.sales_date,
                daily.sku_id,
                daily.sales_point_id,
                daily.net_sales_qty
            FROM sales_daily daily
            WHERE daily.is_deleted = 0
              AND daily.sales_date <= ?
            ORDER BY
                daily.sku_id,
                daily.sales_point_id,
                daily.sales_date
            """;
    private static final String SELECT_DAILY_SQL = """
            SELECT
                daily.sales_date,
                daily.sku_id,
                daily.sales_point_id,
                daily.net_sales_qty
            FROM sales_daily daily
            WHERE daily.is_deleted = 0
              AND daily.sales_date = ?
            ORDER BY
                daily.sku_id,
                daily.sales_point_id,
                daily.sales_date
            """;
    private static final String EXISTS_BOOTSTRAP_SQL = """
            SELECT 1
            FROM sales_daily daily
            WHERE daily.is_deleted = 0
              AND daily.sales_date <= ?
              AND ROWNUM = 1
            """;
    private static final String EXISTS_DAILY_SQL = """
            SELECT 1
            FROM sales_daily daily
            WHERE daily.is_deleted = 0
              AND daily.sales_date = ?
              AND ROWNUM = 1
            """;

    @Bean
    public Job salesDailyCsvExportJob(
            JobRepository jobRepository,
            @Qualifier("salesDailyCsvExportValidationStep") Step validationStep,
            @Qualifier("salesDailyCsvExportStep") Step exportStep,
            @Qualifier("salesDailyCsvUploadStep") Step uploadStep,
            SalesDailyCsvExportFileManager fileManager,
            JobParametersValidator salesDailyCsvExportJobParametersValidator
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(salesDailyCsvExportJobParametersValidator)
                .listener(fileManager)
                .start(validationStep)
                .next(exportStep)
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
                    boolean exists = Boolean.TRUE.equals(jdbcTemplate.query(
                            existsSql(exportMode),
                            statement -> statement.setDate(1, Date.valueOf(baseDate)),
                            (ResultSetExtractor<Boolean>) resultSet -> resultSet.next()
                    ));
                    if (!exists) {
                        throw new IllegalStateException(
                                noDataMessage(exportMode)
                        );
                    }
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
                .sql(selectSql(exportMode))
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

    private static String selectSql(SalesDailyExportMode exportMode) {
        return exportMode == SalesDailyExportMode.BOOTSTRAP
                ? SELECT_BOOTSTRAP_SQL
                : SELECT_DAILY_SQL;
    }

    private static String existsSql(SalesDailyExportMode exportMode) {
        return exportMode == SalesDailyExportMode.BOOTSTRAP
                ? EXISTS_BOOTSTRAP_SQL
                : EXISTS_DAILY_SQL;
    }

    private static String noDataMessage(SalesDailyExportMode exportMode) {
        return exportMode == SalesDailyExportMode.BOOTSTRAP
                ? "기준일 이전의 유효한 SALES_DAILY 데이터가 없습니다."
                : "기준일에 해당하는 유효한 SALES_DAILY 데이터가 없습니다.";
    }
}
