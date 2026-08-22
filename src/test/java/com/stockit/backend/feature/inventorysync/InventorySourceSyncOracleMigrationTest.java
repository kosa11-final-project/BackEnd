package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "INVENTORY_SYNC_ORACLE_MIGRATE", matches = "true")
class InventorySourceSyncOracleMigrationTest {

    @Test
    void migratesTheApprovedOracleSchemaThroughInventorySyncVersion() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        System.getenv("DB_URL"),
                        System.getenv("DB_USERNAME"),
                        System.getenv("DB_PASSWORD")
                )
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        MigrationInfo current = flyway.info().current();

        System.out.println("INVENTORY_SYNC_FLYWAY_MIGRATE={initialVersion=" + result.initialSchemaVersion
                + ", targetVersion=" + result.targetSchemaVersion
                + ", migrationsExecuted=" + result.migrationsExecuted + "}");

        assertThat(result.success).isTrue();
        assertThat(current).isNotNull();
        assertThat(String.valueOf(current.getVersion())).isEqualTo("24");
        assertThat(result.migrationsExecuted).isBetween(0, 4);
    }
}
