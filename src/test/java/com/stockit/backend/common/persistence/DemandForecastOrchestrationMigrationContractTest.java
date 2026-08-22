package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DemandForecastOrchestrationMigrationContractTest {
    private static final String MIGRATION =
            "db/migration/V25__create_demand_forecast_orchestration_schema.sql";
    private static final String NOTIFICATION_MAPPER =
            "mappers/demandforecast/DemandForecastNotificationMapper.xml";

    @Test
    void definesDurableRunBatchStagingAndNotificationContracts() throws IOException {
        String sql = readResource(MIGRATION);

        assertThat(sql).contains(
                "CREATE TABLE demand_forecast_run",
                "CREATE TABLE demand_forecast_import_batch",
                "CREATE TABLE demand_forecast_staging",
                "CONSTRAINT uq_df_import_batch UNIQUE (forecast_run_id, batch_number)",
                "CONSTRAINT uq_df_staging_target UNIQUE (forecast_run_id, sku_id, sales_point_id)",
                "ALTER TABLE demand_forecast ADD (forecast_run_id NUMBER)",
                "ALTER TABLE notification MODIFY (strategy_case_id NULL)",
                "CONSTRAINT uq_notification_dedupe"
        );
    }

    @Test
    void keepsForecastReadersOutOfThePublicationContract() throws IOException {
        String sql = readResource(MIGRATION);

        assertThat(sql).doesNotContain("CREATE OR REPLACE VIEW published_demand_forecast");
        assertThat(sql).contains("Unpublished forecast rows awaiting complete batch receipt");
    }

    @Test
    void sendsPipelineNotificationsToAdminsAndBranchManagersWithoutDuplicates() throws IOException {
        String mapper = readResource(NOTIFICATION_MAPPER);

        assertThat(mapper).contains(
                "SELECT DISTINCT app_user.user_id",
                "app_role.role_code IN ('GREENFOOD_ADMIN', 'BRANCH_MANAGER')"
        );
    }

    private static String readResource(String path) throws IOException {
        ClassLoader classLoader = DemandForecastOrchestrationMigrationContractTest.class
                .getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IOException("Missing resource: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
