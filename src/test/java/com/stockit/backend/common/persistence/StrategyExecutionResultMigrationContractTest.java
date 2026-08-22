package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrategyExecutionResultMigrationContractTest {

    private static final String MIGRATION_PATH =
            "db/migration/V25__create_strategy_execution_result.sql";

    @Test
    void createsOneFinalResultPerSelectionAndValidatesActionDates(@TempDir Path tempDir)
            throws Exception {
        Path migrationFile = tempDir.resolve("V25__create_strategy_execution_result.sql");
        try (InputStream input = requiredResource()) {
            Files.copy(input, migrationFile);
        }

        String jdbcUrl = "jdbc:h2:mem:strategy-result-migration;MODE=Oracle;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE app_user (user_id NUMBER PRIMARY KEY)");
            statement.execute("CREATE TABLE final_strategy_selection (final_selection_id NUMBER PRIMARY KEY)");
            statement.execute("CREATE TABLE inventory_sync_run (inventory_sync_run_id NUMBER PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE strategy_action (
                        strategy_action_id NUMBER PRIMARY KEY,
                        strategy_option_id NUMBER,
                        start_date DATE,
                        end_date DATE,
                        is_deleted NUMBER(1)
                    )
                    """);
            statement.execute("INSERT INTO app_user VALUES (1)");
            statement.execute("INSERT INTO final_strategy_selection VALUES (10)");
        }

        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + tempDir.toAbsolutePath())
                .baselineOnMigrate(true)
                .baselineVersion("24")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO strategy_execution_result (
                        final_selection_id,
                        result_status,
                        planned_start_date,
                        planned_end_date,
                        goal_target_value,
                        start_risk_stock_qty,
                        start_expected_disposal_qty,
                        start_unit_cost,
                        created_by,
                        updated_by
                    ) VALUES (
                        10, 'RUNNING', DATE '2026-08-01', DATE '2026-08-10',
                        100, 100, 30, 2000, 1, 1
                    )
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO strategy_action VALUES (
                        1, 100, DATE '2026-08-11', DATE '2026-08-10', 0
                    )
                    """))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void documentsFinalOutcomeCalculationInputs() throws Exception {
        String migration;
        try (InputStream input = requiredResource()) {
            migration = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        assertThat(migration).contains(
                "CREATE TABLE strategy_execution_result",
                "UNIQUE (final_selection_id)",
                "result_status",
                "planned_start_date",
                "planned_end_date",
                "goal_target_value",
                "goal_actual_value",
                "start_risk_stock_qty",
                "end_risk_stock_qty",
                "start_expected_disposal_qty",
                "end_expected_disposal_qty",
                "estimated_loss_savings_amount",
                "finalized_sync_run_id",
                "SALES_ONLY_V1"
        );
    }

    private static InputStream requiredResource() {
        InputStream input = StrategyExecutionResultMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(MIGRATION_PATH);
        if (input == null) {
            throw new IllegalStateException("Missing test resource: " + MIGRATION_PATH);
        }
        return input;
    }
}
