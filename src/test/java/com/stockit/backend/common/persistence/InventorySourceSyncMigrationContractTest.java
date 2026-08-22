package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Contract checks for the Oracle-only source synchronization migrations.
 *
 * <p>The test deliberately does not execute the migrations on H2. Oracle
 * identity columns, {@code ON DELETE SET NULL}, and virtual-column behavior
 * are not equivalent in H2. The approved execution target for the DDL is an
 * Oracle test schema; this test protects the static contract and the
 * immutability of the existing migration chain in every build.</p>
 */
class InventorySourceSyncMigrationContractTest {

    private static final String V21 = "db/migration/V21__create_inventory_source_sync_schema.sql";
    private static final String V22 = "db/migration/V22__link_risk_assessment_to_inventory_sync.sql";
    private static final String V23 = "db/migration/V23__create_inventory_demo_adjustment.sql";
    private static final String V24 = "db/migration/V24__allow_scheduled_inventory_sync.sql";

    private static final Map<String, String> EXISTING_MIGRATION_SHA256 = new LinkedHashMap<>();

    static {
        EXISTING_MIGRATION_SHA256.put("V1__baseline_schema.sql", "d4dd9898c37712ceda0b0864d39af56aae44f6482c6fd60cc1c335059eac86f3");
        EXISTING_MIGRATION_SHA256.put("V2__update_category_source_constraint.sql", "80363b7bc8f38ca95198f660eb4be05d97207ebd5c218e017d9a8e53431ff21e");
        EXISTING_MIGRATION_SHA256.put("V3__create_inventory_movement.sql", "18c07b3dcaf96fc154a753c721e11d2cafc3ddd559b90cf7fea609ad7aa8e8e9");
        EXISTING_MIGRATION_SHA256.put("V4__alter_category_and_product.sql", "4c635adb714dd8e18e582dac6c5ce23310ae5f5748ee7e566769622c011f6d4e");
        EXISTING_MIGRATION_SHA256.put("V5__add_base_entity_columns.sql", "80b6775e3f2d997e6896036f0f53451969e281c9da8ea5cada85d3d4ccf5aa51");
        EXISTING_MIGRATION_SHA256.put("V6__enforce_base_entity_constraints.sql", "64e56596a80e6cb0412476184d51d55f7e5337f6a5cfa8b08c45b41ae940b497");
        EXISTING_MIGRATION_SHA256.put("V7__seed_greenfood_admin_role.sql", "0a74a009bd27c2ead78cf12099add1941d4bbc7bbc723b1a3d666bc26be65741");
        EXISTING_MIGRATION_SHA256.put("V8__update_risk_score_and_remove_packaging_cost.sql", "b309c38f391f03ba61c124676e3753d98652849ff16eb1418f86dc1dd3bba768");
        EXISTING_MIGRATION_SHA256.put("V9__convert_calculated_columns_to_virtual.sql", "e306c82ada4df1620e53bc23eaa31e66d4fd1b4a9fd7afd849c6636287f5ae18");
        EXISTING_MIGRATION_SHA256.put("V10__reshape_demand_forecast_horizons.sql", "aa0303f3d281b400af4afe323e8c98225a1fd576d898d8da4c320aa29c5cf2ac");
        EXISTING_MIGRATION_SHA256.put("V11__add_demand_forecast_quality_columns.sql", "430ab09bb01ee6b58ad04cd05c9579f953cca07f5bd7a4f3868c46d9f3ef02fb");
        EXISTING_MIGRATION_SHA256.put("V12__create_strategy_forecast_snapshot.sql", "35a8caa05433d9eadea4735b017a06b0d037ddc54e8255e9452271e515185067");
        EXISTING_MIGRATION_SHA256.put("V13__create_dashboard_snapshot.sql", "0322086ed3ab5016345d9704a5094e313e536f97a421e8841b5c6a970877f8b4");
        EXISTING_MIGRATION_SHA256.put("V14__create_sku_cost.sql", "c0372b87597ab460037490d31512a474e7694c481184fa174a4b81ce458dda39");
        EXISTING_MIGRATION_SHA256.put("V15__backfill_sku_cost.sql", "3e7316b90d29108f0943501d31a9d8fd9fd631d3c9bb2dce198c8c9116e21e8b");
        EXISTING_MIGRATION_SHA256.put("V16__create_statistics_snapshot.sql", "8aa5bf3ddb17fc4ce5814183f58d06fab5b37fca988aa8c47a5c107ac08548a2");
        EXISTING_MIGRATION_SHA256.put("V17__expand_statistics_snapshot_scope.sql", "ddbdd621a1175da543647e571d5a59eb74dc720a9753d14b3484e03f3b199047");
        EXISTING_MIGRATION_SHA256.put("V18__align_ai_strategy_schema.sql", "83cff6e24ebe5b0a628edd3281d6c5eb4e19609dad38868d5820146f48c7c586");
        EXISTING_MIGRATION_SHA256.put("V19__add_strategy_generation_stage.sql", "e46e193127fd8ffec8562a95f5532e297b91b563622ad11e3103398157300a00");
        EXISTING_MIGRATION_SHA256.put("V20__add_final_strategy_last_synced_at.sql", "f274feb9c4a0b28b50791e16d6567e9274f266cfc66c8b545c6963db9c12d649");
    }

    @Test
    void keepsV1ThroughV20ByteForByteUnchanged() throws IOException {
        EXISTING_MIGRATION_SHA256.forEach((fileName, expectedHash) -> {
            try {
                assertThat(sha256(readResource("db/migration/" + fileName)))
                        .as("checksum for %s", fileName)
                        .isEqualTo(expectedHash);
            } catch (IOException exception) {
                throw new AssertionError("Missing migration: " + fileName, exception);
            }
        });
    }

    @Test
    void declaresAllSourceAndControlTablesWithOracleKeysAndGuards() throws IOException {
        String migration = readResource(V21);

        assertThat(migration).contains(
                "CREATE TABLE inventory_sync_run",
                "CREATE TABLE inventory_source_offline",
                "CREATE TABLE inventory_source_ecommerce",
                "CREATE TABLE inventory_source_greeting",
                "CREATE TABLE inventory_source_warehouse",
                "CREATE TABLE inventory_source_row_map",
                "CREATE TABLE inventory_source_state",
                "CREATE TABLE inventory_sync_run_source",
                "CREATE TABLE inventory_sync_error"
        );

        assertThat(migration).contains(
                "GENERATED BY DEFAULT ON NULL AS IDENTITY",
                "CONSTRAINT uq_isync_active_scope UNIQUE (active_scope_key)",
                "CONSTRAINT uq_isync_client_request UNIQUE (client_request_id)",
                "CONSTRAINT uq_isync_run_source UNIQUE (inventory_sync_run_id, source_type)",
                "CONSTRAINT uq_isrc_map_key UNIQUE (source_type, source_record_key)",
                "ON DELETE SET NULL",
                "ix_isync_request_rate",
                "ix_isync_error_run"
        );
        assertThat(migration).containsPattern(Pattern.compile(
                "CREATE\\s+INDEX\\s+ix_isync_request_rate\\s+ON\\s+inventory_sync_run\\s*\\(requested_by,\\s*requested_at\\)",
                Pattern.CASE_INSENSITIVE
        ));
        assertThat(migration).containsPattern(Pattern.compile(
                "CREATE\\s+INDEX\\s+ix_isync_error_run\\s+ON\\s+inventory_sync_error\\s*\\(inventory_sync_run_id,\\s*source_type\\)",
                Pattern.CASE_INSENSITIVE
        ));

        assertThat(migration).containsPattern(Pattern.compile(
                "CONSTRAINT ck_isync_status CHECK[\\s\\S]*'LAUNCH_FAILED'",
                Pattern.CASE_INSENSITIVE
        ));
        assertThat(migration).containsPattern(Pattern.compile(
                "CONSTRAINT ck_isrc_map_type CHECK[\\s\\S]*'WAREHOUSE'",
                Pattern.CASE_INSENSITIVE
        ));
        assertThat(migration).containsPattern(Pattern.compile(
                "CONSTRAINT ck_isrc_off_qty CHECK[\\s\\S]*available_stock_count >= 0",
                Pattern.CASE_INSENSITIVE
        ));
        assertThat(migration).containsPattern(Pattern.compile(
                "CONSTRAINT ck_isync_active_scope CHECK[\\s\\S]*active_scope_key IS NULL",
                Pattern.CASE_INSENSITIVE
        ));
    }

    @Test
    void declaresSourceSpecificColumnsAndAuditLineage() throws IOException {
        String migration = readResource(V21);

        assertThat(migration).contains(
                "offline_source_code",
                "branch_option_no",
                "mall_code",
                "seller_option_no",
                "greeting_item_id",
                "production_batch_no",
                "logistics_center_code",
                "physical_available_qty",
                "assigned_sales_point_code",
                "source_modified_at",
                "fixture_generation_id",
                "record_hash",
                "synced_record_hash",
                "last_sync_run_id",
                "last_success_sync_run_id",
                "created_at",
                "updated_at",
                "created_by",
                "updated_by",
                "is_deleted"
        );
        assertThat(migration).containsPattern(Pattern.compile(
                "(?i)created_at\\s+TIMESTAMP\\s+DEFAULT\\s+SYSTIMESTAMP\\s+NOT NULL"
        ));
        assertThat(migration).containsPattern(Pattern.compile(
                "(?i)updated_at\\s+TIMESTAMP\\s+DEFAULT\\s+SYSTIMESTAMP\\s+NOT NULL"
        ));
        assertThat(migration).containsPattern(Pattern.compile("(?i)created_by\\s+NUMBER\\s+NOT NULL"));
        assertThat(migration).containsPattern(Pattern.compile("(?i)updated_by\\s+NUMBER\\s+NOT NULL"));
        assertThat(migration).containsPattern(Pattern.compile(
                "(?i)is_deleted\\s+NUMBER\\(1\\)\\s+DEFAULT\\s+0\\s+NOT NULL"
        ));
    }

    @Test
    void linksRiskAssessmentToSyncWithoutRewritingExistingRowsOrAddingProviderColumns() throws IOException {
        String migration = readResource(V22);

        assertThat(migration)
                .contains("ALTER TABLE risk_assessment ADD")
                .contains("inventory_sync_run_id")
                .contains("CREATE INDEX ix_risk_sync_run");
        assertThat(migration).doesNotContainIgnoringCase("gemini", "llm", "model_name");
        assertThat(migration).containsPattern(Pattern.compile(
                "(?i)REFERENCES\\s+inventory_sync_run\\s*\\(inventory_sync_run_id\\)\\s+ON DELETE SET NULL"
        ));
        assertThat(migration).doesNotContain("UPDATE risk_assessment");
    }

    @Test
    void neverAddsAStoredTotalQuantityColumnToTheNewSchema() throws IOException {
        String migration = readResource(V21) + readResource(V22);

        assertThat(migration).doesNotContain("total_qty NUMBER");
        assertThat(migration).doesNotContain("ALTER TABLE inventory_balance");
    }

    @Test
    void keepsDemoAdjustmentSourceOnlyAndIdempotentAtTheDatabaseBoundary() throws IOException {
        String migration = readResource(V23);

        assertThat(migration)
                .contains("CREATE TABLE inventory_demo_adjustment")
                .contains("CONSTRAINT uq_inventory_demo_item_request UNIQUE (request_id, source_type, source_record_key)")
                .contains("CONSTRAINT ck_demo_adjustment_payload CHECK (payload_json IS JSON)")
                .contains("CONSTRAINT fk_demo_adjustment_user FOREIGN KEY (requested_by)");
        assertThat(migration).doesNotContainIgnoringCase("inventory_balance");
        assertThat(migration).containsPattern(Pattern.compile(
                "CREATE\\s+INDEX\\s+ix_demo_adjustment_rate\\s+ON\\s+inventory_demo_adjustment\\s*\\(requested_by,\\s*requested_at\\)",
                Pattern.CASE_INSENSITIVE
        ));
    }

    @Test
    void onlyQueuedRunsCanBeClaimedByAWorker() throws IOException {
        String mapper = readResource("mappers/inventorysync/InventorySyncRunMapper.xml");

        assertThat(mapper).containsPattern(Pattern.compile(
                "(?s)<update id=\"markRunning\">.*?run_status = 'QUEUED'",
                Pattern.CASE_INSENSITIVE
        ));
    }

    @Test
    void allowsTheDailyScheduledTriggerWithoutChangingTheExistingMigrationChain() throws IOException {
        String migration = readResource(V24);

        assertThat(migration)
                .contains("DROP CONSTRAINT ck_isync_trigger")
                .contains("CHECK (trigger_type IN ('MANUAL', 'SCHEDULED'))")
                .contains("UPDATE app_user")
                .contains("active_yn = 'Y'")
                .contains("login_id = '__system__'");
    }

    @Test
    void scheduledRunsUseTheSystemAuditPrincipalAndDateBasedIdempotencyInTheMapperContract() throws IOException {
        String mapper = readResource("mappers/inventorysync/InventorySyncRunMapper.xml");

        assertThat(mapper)
                .contains("login_id = '__system__'")
                .contains("#{triggerType}")
                .contains("main_batch_job_execution_id = NULL");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = InventorySourceSyncMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte valueByte : digest) {
                hex.append(String.format("%02x", valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the JRE", exception);
        }
    }
}
