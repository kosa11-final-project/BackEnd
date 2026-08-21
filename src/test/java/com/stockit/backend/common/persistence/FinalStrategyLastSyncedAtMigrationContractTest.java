package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinalStrategyLastSyncedAtMigrationContractTest {

    private static final String MIGRATION_PATH =
            "db/migration/V20__add_final_strategy_last_synced_at.sql";

    @Test
    void addsNullableLastSyncedAtToFinalStrategySelection() throws IOException {
        String migration = readResource(MIGRATION_PATH);

        assertThat(migration).contains(
                "ALTER TABLE final_strategy_selection ADD (",
                "last_synced_at TIMESTAMP",
                "COMMENT ON COLUMN final_strategy_selection.last_synced_at",
                "동기화 전에는 NULL"
        );
        assertThat(migration).doesNotContain(
                "last_synced_at TIMESTAMP NOT NULL",
                "DEFAULT"
        );
    }

    @Test
    void doesNotIntroduceSyncHistoryOrJobTables() throws IOException {
        String migration = readResource(MIGRATION_PATH).toUpperCase();

        assertThat(migration).doesNotContain(
                "STRATEGY_SYNC_HISTORY",
                "SYNC_JOB",
                "CREATE TABLE"
        );
    }

    @Test
    void appliesWithFlywayAndLeavesExistingRowsUnsynced(@TempDir Path tempDir)
            throws Exception {
        Path migrationFile = tempDir.resolve("V20__add_final_strategy_last_synced_at.sql");
        try (InputStream input = getRequiredResource(MIGRATION_PATH)) {
            Files.copy(input, migrationFile);
        }

        String jdbcUrl = "jdbc:h2:mem:final-strategy-sync;MODE=Oracle;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE final_strategy_selection (
                        final_selection_id NUMBER PRIMARY KEY
                    )
                    """);
            statement.execute("INSERT INTO final_strategy_selection VALUES (1)");
        }

        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("filesystem:" + tempDir.toAbsolutePath())
                .baselineOnMigrate(true)
                .baselineVersion("19")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT last_synced_at
                     FROM final_strategy_selection
                     WHERE final_selection_id = 1
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getTimestamp("last_synced_at")).isNull();
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = getRequiredResource(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream getRequiredResource(String path) {
        InputStream input = FinalStrategyLastSyncedAtMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("Missing test resource: " + path);
        }
        return input;
    }
}
