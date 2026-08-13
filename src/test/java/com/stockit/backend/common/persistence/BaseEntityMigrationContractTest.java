package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class BaseEntityMigrationContractTest {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "CREATE\\s+TABLE\\s+([a-z0-9_]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> EXISTING_CREATED_AT = Set.of("ml_model_version");
    private static final Set<String> EXISTING_CREATED_BY = Set.of(
            "strategy_case",
            "final_strategy_selection"
    );

    @Test
    void coversEveryBusinessTableWithBaseEntityColumnsAndConstraints() throws IOException {
        String baseline = readResource("db/migration/V1__baseline_schema.sql");
        String inventoryMovement = readResource("db/migration/V3__create_inventory_movement.sql");
        String addColumns = readResource("db/migration/V4__add_base_entity_columns.sql");
        String enforceConstraints = readResource("db/migration/V5__enforce_base_entity_constraints.sql");

        Set<String> tables = extractTables(baseline + System.lineSeparator() + inventoryMovement);
        assertThat(tables).hasSize(37);

        for (String table : tables) {
            String addBlock = extractAddBlock(addColumns, table);

            assertThat(addBlock).contains("updated_at", "updated_by", "is_deleted");
            if (!EXISTING_CREATED_AT.contains(table)) {
                assertThat(addBlock).contains("created_at");
            }
            if (!EXISTING_CREATED_BY.contains(table)) {
                assertThat(addBlock).contains("created_by");
            }

            assertThat(addColumns).contains("UPDATE " + table);
            assertThat(enforceConstraints).contains(
                    "'" + table + "'",
                    "COMMENT ON COLUMN " + table + ".created_at",
                    "COMMENT ON COLUMN " + table + ".is_deleted"
            );
        }

        assertThat(enforceConstraints).contains(
                "enforce_column(v_tables(i), 'created_at', 'TIMESTAMP', 'SYSTIMESTAMP')",
                "enforce_column(v_tables(i), 'updated_at', 'TIMESTAMP', 'SYSTIMESTAMP')",
                "enforce_column(v_tables(i), 'created_by', 'NUMBER')",
                "enforce_column(v_tables(i), 'updated_by', 'NUMBER')",
                "enforce_column(v_tables(i), 'is_deleted', 'NUMBER(1)', '0')",
                "'ck_' || v_tables(i) || '_deleted'",
                "'fk_' || v_tables(i) || '_created_by'",
                "'fk_' || v_tables(i) || '_updated_by'",
                "v_tables(i) NOT IN ('strategy_case', 'final_strategy_selection')"
        );
    }

    @Test
    void createsInactiveSystemActorBeforeBackfill() throws IOException {
        String migration = readResource("db/migration/V4__add_base_entity_columns.sql");

        int organizationSeed = migration.indexOf("MERGE INTO organization");
        int systemUserSeed = migration.indexOf("MERGE INTO app_user");
        int firstBackfill = migration.indexOf("UPDATE organization");

        assertThat(organizationSeed).isGreaterThanOrEqualTo(0);
        assertThat(systemUserSeed).isGreaterThan(organizationSeed);
        assertThat(firstBackfill).isGreaterThan(systemUserSeed);
        assertThat(migration).contains(
                "'__system__' AS login_id",
                "UPDATE SET target.active_yn = 'N'",
                "VALUES (source.organization_id, source.login_id, source.password_hash, source.user_name, 'N')"
        );
    }

    @Test
    void safelyResumesAfterPartiallyAppliedOracleDdl() throws IOException {
        String migration = readResource("db/migration/V5__enforce_base_entity_constraints.sql");

        assertThat(migration).contains(
                "IF v_nullable = 'N' AND p_default_value IS NULL THEN",
                "IF v_nullable = 'Y' THEN",
                "FROM user_constraints",
                "IF v_constraint_count = 0 THEN"
        );
    }

    private static Set<String> extractTables(String ddl) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(ddl);
        Set<String> tables = new LinkedHashSet<>();
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase());
        }
        return tables;
    }

    private static String extractAddBlock(String migration, String table) {
        Pattern pattern = Pattern.compile(
                "ALTER TABLE " + Pattern.quote(table) + "\\s+ADD \\((.*?)\\);",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(migration);
        assertThat(matcher.find()).as("audit column block for %s", table).isTrue();
        return matcher.group(1).toLowerCase();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = BaseEntityMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
