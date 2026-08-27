package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class InventoryTransferCostMigrationContractTest {

    private static final String MIGRATION_PATH =
            "db/migration/V30__create_inventory_transfer_cost_schema.sql";

    @Test
    void createsDirectionalRouteAndGlobalCostPolicySchema() throws IOException {
        String migration = readMigration();

        assertThat(migration).contains(
                "CREATE TABLE inventory_transfer_route",
                "source_location_type",
                "source_warehouse_id",
                "source_sales_point_id",
                "destination_location_type",
                "destination_warehouse_id",
                "destination_sales_point_id",
                "distance_km",
                "distance_source",
                "CREATE UNIQUE INDEX uq_itr_active_path",
                "CREATE TABLE inventory_transfer_cost_policy",
                "cost_per_kg_km",
                "effective_from",
                "effective_to"
        );
    }

    @Test
    void addsActionCalculationSnapshotAndSimulationEconomicResult() throws IOException {
        String migration = readMigration();

        assertThat(migration).contains(
                "transfer_route_id",
                "transfer_cost_policy_id",
                "movement_cost_status",
                "movement_weight_kg",
                "movement_distance_km",
                "movement_cost_per_kg_km",
                "LEGACY_EXCLUDED",
                "avoided_holding_cost",
                "net_effect",
                "strategy_simulation.avoided_disposal_cost"
        );
    }

    @Test
    void leavesEnvironmentSpecificDataAndExcludedCostFactorsOutOfFlyway() throws IOException {
        String migration = readMigration().toUpperCase();

        assertThat(migration).doesNotContain(
                "INSERT INTO INVENTORY_TRANSFER_ROUTE",
                "MERGE INTO INVENTORY_TRANSFER_ROUTE",
                "INSERT INTO INVENTORY_TRANSFER_COST_POLICY",
                "MERGE INTO INVENTORY_TRANSFER_COST_POLICY",
                "LEAD_TIME",
                "HANDLING_COST",
                "PACKAGING_WEIGHT"
        );
    }

    private static String readMigration() throws IOException {
        try (InputStream input = InventoryTransferCostMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(MIGRATION_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing migration: " + MIGRATION_PATH);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
