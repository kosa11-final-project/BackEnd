package com.stockit.backend.feature.demandforecast.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import com.stockit.backend.feature.demandforecast.vo.DemandForecastVO;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastRunVO;
import com.stockit.backend.feature.demandforecast.vo.DemandForecastStagingVO;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/demand-forecast-import-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DemandForecastMapperTest {

    @Autowired
    private DemandForecastMapper demandForecastMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resolvesModelAndValidatesReferenceIdsInBulk() {
        assertThat(demandForecastMapper.selectModelVersionId("stockit-demand-lightgbm", "1"))
                .isEqualTo(7L);
        assertThat(demandForecastMapper.countExistingSkus(List.of(101L, 102L)))
                .isEqualTo(2);
        assertThat(demandForecastMapper.countExistingSalesPoints(List.of(10L)))
                .isEqualTo(1);
    }

    @Test
    void insertsThenUpdatesByForecastUniqueTarget() {
        DemandForecastVO initial = forecast(new BigDecimal("12.300"), "HIGH");
        demandForecastMapper.mergeDemandForecasts(List.of(initial));

        DemandForecastVO updated = forecast(new BigDecimal("13.500"), "MEDIUM");
        updated.setPredictedQtyD14(new BigDecimal("25.500"));
        demandForecastMapper.mergeDemandForecasts(List.of(updated));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demand_forecast",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT predicted_qty_d7 FROM demand_forecast",
                BigDecimal.class
        )).isEqualByComparingTo("13.500");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT confidence_level FROM demand_forecast",
                String.class
        )).isEqualTo("MEDIUM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_by FROM demand_forecast",
                Long.class
        )).isEqualTo(99L);
    }

    @Test
    void stagesCompleteBatchAndPublishesOnlyDuringFinalization() {
        jdbcTemplate.update("""
                INSERT INTO demand_forecast_run (
                    forecast_run_id, client_request_id, trigger_type, base_date,
                    azure_job_id, run_status, current_stage, created_by, updated_by
                ) VALUES (501, 'scheduled-20260731', 'SCHEDULED', DATE '2026-07-31',
                          'azure-job-501', 'RUNNING', 'IMPORT_REQUESTING', 99, 99)
                """);

        DemandForecastRunVO run = demandForecastMapper.selectRunByAzureJobIdForUpdate("azure-job-501");
        assertThat(run.getForecastRunId()).isEqualTo(501L);
        assertThat(demandForecastMapper.initializeImportManifest(
                501L, 7L, LocalDate.of(2026, 7, 31), 1, 1L, 99L
        )).isEqualTo(1);

        DemandForecastStagingVO staging = stagingForecast();
        demandForecastMapper.insertStagingForecasts(List.of(staging));
        demandForecastMapper.insertImportBatch(501L, 1, 1, "a".repeat(64), 99L);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demand_forecast", Integer.class
        )).isZero();
        assertThat(demandForecastMapper.countReceivedBatches(501L)).isEqualTo(1);
        assertThat(demandForecastMapper.sumReceivedItems(501L)).isEqualTo(1L);
        assertThat(demandForecastMapper.countStagingForecasts(501L)).isEqualTo(1);

        assertThat(demandForecastMapper.updateImportProgress(501L, 1, 1L, 1L, 99L)).isEqualTo(1);
        demandForecastMapper.softDeleteObsoleteForecasts(501L, 99L);
        demandForecastMapper.mergeStagingForecasts(501L, 99L);
        assertThat(demandForecastMapper.markRunSucceeded(501L, 99L)).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demand_forecast", Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT forecast_run_id FROM demand_forecast", Long.class
        )).isEqualTo(501L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT run_status FROM demand_forecast_run WHERE forecast_run_id = 501",
                String.class
        )).isEqualTo("SUCCEEDED");
    }

    @Test
    void acceptsOmittedTotalItemsWhileImportIsInProgress() {
        jdbcTemplate.update("""
                INSERT INTO demand_forecast_run (
                    forecast_run_id, client_request_id, trigger_type, base_date,
                    azure_job_id, run_status, current_stage, created_by, updated_by
                ) VALUES (502, 'scheduled-20260822', 'SCHEDULED', DATE '2026-08-22',
                          'azure-job-502', 'RUNNING', 'AZURE_POLLING', 99, 99)
                """);

        assertThat(demandForecastMapper.initializeImportManifest(
                502L, 7L, LocalDate.of(2026, 8, 22), 2, null, 99L
        )).isEqualTo(1);
        assertThat(demandForecastMapper.updateImportProgress(
                502L, 1, 1L, null, 99L
        )).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_items FROM demand_forecast_run WHERE forecast_run_id = 502",
                Long.class
        )).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_stage FROM demand_forecast_run WHERE forecast_run_id = 502",
                String.class
        )).isEqualTo("IMPORTING");
    }

    private static DemandForecastVO forecast(BigDecimal d7, String confidenceLevel) {
        return DemandForecastVO.forImport(
                101L,
                10L,
                7L,
                LocalDate.of(2026, 7, 31),
                d7,
                new BigDecimal("24.800"),
                new BigDecimal("51.200"),
                new BigDecimal("103.700"),
                new BigDecimal("157.100"),
                "LIGHTGBM",
                confidenceLevel,
                99L
        );
    }

    private static DemandForecastStagingVO stagingForecast() {
        DemandForecastStagingVO forecast = new DemandForecastStagingVO();
        forecast.setForecastRunId(501L);
        forecast.setBatchNumber(1);
        forecast.setSkuId(101L);
        forecast.setSalesPointId(10L);
        forecast.setPredictedQtyD7(new BigDecimal("12.300"));
        forecast.setPredictedQtyD14(new BigDecimal("24.800"));
        forecast.setPredictedQtyD30(new BigDecimal("51.200"));
        forecast.setPredictedQtyD60(new BigDecimal("103.700"));
        forecast.setPredictedQtyD90(new BigDecimal("157.100"));
        forecast.setForecastSource("LIGHTGBM");
        forecast.setConfidenceLevel("HIGH");
        forecast.setCreatedBy(99L);
        return forecast;
    }
}
