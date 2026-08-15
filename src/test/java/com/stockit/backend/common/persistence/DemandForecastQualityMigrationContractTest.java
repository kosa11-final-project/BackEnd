package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DemandForecastQualityMigrationContractTest {

    @Test
    void addsBackfillsAndConstrainsForecastQualityColumnsInOrder() throws IOException {
        String migration = readResource(
                "db/migration/V11__add_demand_forecast_quality_columns.sql"
        );

        int addColumns = migration.indexOf("ALTER TABLE demand_forecast ADD (");
        int backfill = migration.indexOf("UPDATE demand_forecast");
        int enforceNotNull = migration.indexOf("ALTER TABLE demand_forecast MODIFY (");
        int sourceConstraint = migration.indexOf("ck_demand_forecast_source");
        int confidenceConstraint = migration.indexOf("ck_demand_forecast_confidence");
        int columnComments = migration.indexOf("COMMENT ON COLUMN");

        assertThat(addColumns).isGreaterThanOrEqualTo(0);
        assertThat(backfill).isGreaterThan(addColumns);
        assertThat(enforceNotNull).isGreaterThan(backfill);
        assertThat(sourceConstraint).isGreaterThan(enforceNotNull);
        assertThat(confidenceConstraint).isGreaterThan(sourceConstraint);
        assertThat(columnComments).isGreaterThan(confidenceConstraint);

        assertThat(migration).contains(
                "forecast_source  VARCHAR2(40)",
                "confidence_level VARCHAR2(10)",
                "SET forecast_source = 'DUMMY_BASELINE'",
                "confidence_level = 'LOW'",
                "forecast_source  NOT NULL",
                "confidence_level NOT NULL"
        );
    }

    @Test
    void allowsOnlySupportedSourcesAndConfidenceLevels() throws IOException {
        String migration = readResource(
                "db/migration/V11__add_demand_forecast_quality_columns.sql"
        );

        assertThat(migration).contains(
                "'LIGHTGBM'",
                "'SAME_SKU_OTHER_POINT'",
                "'CATEGORY_SALES_POINT_MEDIAN'",
                "'CATEGORY_GLOBAL_MEDIAN'",
                "'MANUAL_INITIAL'",
                "'DUMMY_BASELINE'",
                "CHECK (confidence_level IN ('HIGH', 'MEDIUM', 'LOW'))",
                "COMMENT ON COLUMN demand_forecast.forecast_source",
                "COMMENT ON COLUMN demand_forecast.confidence_level"
        );
    }

    @Test
    void keepsExistingForecastShapeAndConstraintsOutOfScope() throws IOException {
        String qualityMigration = readResource(
                "db/migration/V11__add_demand_forecast_quality_columns.sql"
        );
        String horizonMigration = readResource(
                "db/migration/V10__reshape_demand_forecast_horizons.sql"
        );

        assertThat(qualityMigration).doesNotContain(
                "history_days",
                "fallback_reason",
                "staging",
                "predicted_qty_d7",
                "predicted_qty_d14",
                "predicted_qty_d30",
                "predicted_qty_d60",
                "predicted_qty_d90"
        );
        assertThat(horizonMigration).contains(
                "CONSTRAINT uq_demand_forecast_target",
                "CONSTRAINT ck_demand_forecast_qty_cumulative",
                "predicted_qty_d7 <= predicted_qty_d14",
                "predicted_qty_d14 <= predicted_qty_d30",
                "predicted_qty_d30 <= predicted_qty_d60",
                "predicted_qty_d60 <= predicted_qty_d90"
        );
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = DemandForecastQualityMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
