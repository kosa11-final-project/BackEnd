package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INVENTORY_SYNC_ORACLE_PREFLIGHT", matches = "true")
class InventorySourceSyncOraclePreflightTest {

    private static final List<String> REQUIRED_BATCH_TABLES = List.of(
            "BATCH_JOB_INSTANCE",
            "BATCH_JOB_EXECUTION",
            "BATCH_JOB_EXECUTION_PARAMS",
            "BATCH_STEP_EXECUTION",
            "BATCH_STEP_EXECUTION_CONTEXT",
            "BATCH_JOB_EXECUTION_CONTEXT"
    );

    private static final List<String> REQUIRED_BATCH_SEQUENCES = List.of(
            "BATCH_JOB_SEQ",
            "BATCH_JOB_EXECUTION_SEQ",
            "BATCH_STEP_EXECUTION_SEQ"
    );

    private static final List<String> SOURCE_SYNC_TABLES = List.of(
            "INVENTORY_SOURCE_OFFLINE",
            "INVENTORY_SOURCE_ECOMMERCE",
            "INVENTORY_SOURCE_GREETING",
            "INVENTORY_SOURCE_WAREHOUSE",
            "INVENTORY_SOURCE_ROW_MAP",
            "INVENTORY_SOURCE_STATE",
            "INVENTORY_SYNC_RUN",
            "INVENTORY_SYNC_RUN_SOURCE",
            "INVENTORY_SYNC_ERROR",
            "INVENTORY_DEMO_ADJUSTMENT"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.hikari.schema", () -> "KOSA");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.inventory-sync.schedule.enabled", () -> "false");
        registry.add("app.inventory-sync.batch-enabled", () -> "false");
    }

    @Test
    void verifiesCanonicalBaselineFlywayAndBatchMetadataWithoutWriting() {
        String flywayTable = jdbcTemplate.queryForObject(
                "SELECT table_name FROM user_tables WHERE UPPER(table_name) = 'FLYWAY_SCHEMA_HISTORY'",
                String.class
        );
        Map<String, String> flywayColumns = jdbcTemplate.query(
                "SELECT column_name FROM user_tab_columns WHERE table_name = ?",
                (resultSet, rowNumber) -> resultSet.getString(1),
                flywayTable
        ).stream().collect(java.util.stream.Collectors.toMap(String::toUpperCase, name -> name));
        String flywayHistory = identifier(flywayTable);
        String versionColumn = identifier(flywayColumns.get("VERSION"));
        String successColumn = identifier(flywayColumns.get("SUCCESS"));

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", queryString("SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM dual"));
        evidence.put("databaseTimestamp", queryString("SELECT TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD\"T\"HH24:MI:SS.FF3TZH:TZM') FROM dual"));
        evidence.put("flywayMaxVersion", queryString(
                "SELECT NVL('V' || TO_CHAR(MAX(TO_NUMBER(REGEXP_SUBSTR(" + versionColumn
                        + ", '^[0-9]+')))), 'NONE') FROM " + flywayHistory
                        + " WHERE " + successColumn + " = 1"
        ));
        evidence.put("flywayFailedCount", queryLong(
                "SELECT COUNT(*) FROM " + flywayHistory + " WHERE " + successColumn + " = 0"
        ));
        evidence.put("batchTableCount", objectCount("user_tables", "table_name", REQUIRED_BATCH_TABLES));
        evidence.put("batchSequenceCount", objectCount("user_sequences", "sequence_name", REQUIRED_BATCH_SEQUENCES));
        evidence.put("sourceSyncTableCount", objectCount("user_tables", "table_name", SOURCE_SYNC_TABLES));
        evidence.put("activeLotCount", queryLong("SELECT COUNT(*) FROM lot WHERE is_deleted = 0"));
        evidence.put("activeBalanceCount", queryLong("SELECT COUNT(*) FROM inventory_balance WHERE is_deleted = 0"));
        evidence.put("onHandSum", queryDecimal("SELECT NVL(SUM(on_hand_qty), 0) FROM inventory_balance WHERE is_deleted = 0"));
        evidence.put("reservedSum", queryDecimal("SELECT NVL(SUM(reserved_qty), 0) FROM inventory_balance WHERE is_deleted = 0"));
        evidence.put("allocatedBalanceCount", queryLong("""
                SELECT COUNT(*) FROM inventory_balance
                 WHERE is_deleted = 0 AND allocated_sales_point_id IS NOT NULL
                """));
        evidence.put("unassignedBalanceCount", queryLong("""
                SELECT COUNT(*) FROM inventory_balance
                 WHERE is_deleted = 0 AND allocated_sales_point_id IS NULL
                """));
        evidence.put("unassignedOnHandSum", queryDecimal("""
                SELECT NVL(SUM(on_hand_qty), 0) FROM inventory_balance
                 WHERE is_deleted = 0 AND allocated_sales_point_id IS NULL
                """));
        evidence.put("danglingRelationCount", queryLong("""
                SELECT COUNT(*)
                  FROM inventory_balance balance
                 WHERE balance.is_deleted = 0
                   AND (
                       NOT EXISTS (SELECT 1 FROM lot WHERE lot_id = balance.lot_id AND is_deleted = 0)
                       OR NOT EXISTS (SELECT 1 FROM sku WHERE sku_id = balance.sku_id AND is_deleted = 0)
                       OR NOT EXISTS (SELECT 1 FROM warehouse WHERE warehouse_id = balance.warehouse_id)
                   )
                """));
        evidence.put("negativeQuantityCount", queryLong("""
                SELECT COUNT(*) FROM inventory_balance
                 WHERE is_deleted = 0 AND (on_hand_qty < 0 OR reserved_qty < 0)
                """));
        evidence.put("duplicateBalanceKeyGroups", queryLong("""
                SELECT COUNT(*)
                  FROM (
                        SELECT sku_id, lot_id, warehouse_id, stock_sales_point_id,
                               allocated_sales_point_id, COUNT(*) row_count
                          FROM inventory_balance
                         WHERE is_deleted = 0
                         GROUP BY sku_id, lot_id, warehouse_id, stock_sales_point_id,
                                  allocated_sales_point_id
                        HAVING COUNT(*) > 1
                       )
                """));

        System.out.println("INVENTORY_SYNC_ORACLE_PREFLIGHT=" + evidence);

        assertThat(evidence.get("schema")).isEqualTo("KOSA");
        assertThat(evidence.get("flywayFailedCount")).isEqualTo(0L);
        assertThat(evidence.get("batchTableCount")).isEqualTo((long) REQUIRED_BATCH_TABLES.size());
        assertThat(evidence.get("batchSequenceCount")).isEqualTo((long) REQUIRED_BATCH_SEQUENCES.size());
        assertThat(evidence.get("activeLotCount")).as("active LOT baseline must exist").isNotEqualTo(0L);
        assertThat(evidence.get("activeBalanceCount")).as("active inventory baseline must exist").isNotEqualTo(0L);
        assertThat(evidence.get("danglingRelationCount")).isEqualTo(0L);
        assertThat(evidence.get("negativeQuantityCount")).isEqualTo(0L);
        assertThat(evidence.get("duplicateBalanceKeyGroups")).isEqualTo(0L);
    }

    @Test
    void validatesAppliedFlywayChecksumsAndPendingSourceMigrationsWithoutMigrating() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        System.getenv("DB_URL"),
                        System.getenv("DB_USERNAME"),
                        System.getenv("DB_PASSWORD")
                )
                .locations("classpath:db/migration")
                .ignoreMigrationPatterns("*:pending")
                .load();

        ValidateResult validation = flyway.validateWithResult();
        List<String> pendingVersions = java.util.Arrays.stream(flyway.info().pending())
                .map(MigrationInfo::getVersion)
                .map(String::valueOf)
                .toList();
        String currentVersion = String.valueOf(flyway.info().current().getVersion());

        System.out.println("INVENTORY_SYNC_FLYWAY_PREFLIGHT={currentVersion=" + currentVersion
                + ", pendingVersions=" + pendingVersions
                + ", validationSuccessful=" + validation.validationSuccessful
                + ", validationErrors=" + validation.getAllErrorMessages() + "}");

        assertThat(validation.validationSuccessful)
                .as("existing Flyway checksums and migration resolution must validate")
                .isTrue();
        int current = Integer.parseInt(currentVersion);
        assertThat(current).isBetween(20, 27);
        List<String> expectedPending = java.util.stream.IntStream.rangeClosed(current + 1, 27)
                .mapToObj(String::valueOf)
                .toList();
        assertThat(pendingVersions).containsExactlyElementsOf(expectedPending);
    }

    private long objectCount(String dictionaryView, String nameColumn, List<String> names) {
        String placeholders = String.join(", ", names.stream().map(ignored -> "?").toList());
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + dictionaryView + " WHERE " + nameColumn + " IN (" + placeholders + ")",
                Long.class,
                names.toArray()
        );
    }

    private long queryLong(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private BigDecimal queryDecimal(String sql) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }

    private String queryString(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
    }

    private String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("unexpected Oracle dictionary identifier");
        }
        return "\"" + value + "\"";
    }
}
