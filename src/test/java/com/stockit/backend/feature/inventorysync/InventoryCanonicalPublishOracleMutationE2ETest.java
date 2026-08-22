package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.stockit.backend.feature.inventorysync.dto.InventorySyncRunResponse;
import com.stockit.backend.feature.inventorysync.dto.InventorySyncStartRequest;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSubmissionService;

/**
 * 운영과 분리된 승인 Oracle에서만 실행하는 변경·복원 검증입니다.
 * 일반 테스트에서는 절대 실행되지 않으며, 한 원천 행을 변경한 뒤 같은 테스트에서 원복합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INVENTORY_SYNC_ORACLE_MUTATION_E2E", matches = "true")
class InventoryCanonicalPublishOracleMutationE2ETest {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED", "FAILED", "SOURCE_CHANGED", "CANCELLED"
    );

    @Autowired
    private InventorySyncSubmissionService submissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.hikari.schema", () -> "KOSA");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("app.inventory-sync.batch-enabled", () -> "true");
        registry.add("app.inventory-sync.schedule.enabled", () -> "false");
        registry.add("app.inventory-sync.demo-enabled", () -> "false");
    }

    @Test
    void sourceMutationPublishesAllMappedTargetsAndRestoresTheBaseline() throws InterruptedException {
        SourceFixture original = selectFixture();
        CanonicalSnapshot canonicalBefore = selectCanonical(original);
        assertThat(canonicalBefore).isEqualTo(original.toCanonicalSnapshot());
        assertThat(activeSkuCostCount()).isZero();

        SourceFixture mutated = original.mutated(selectAlternativeCategory(original.categoryId()));
        try {
            applySourceValues(mutated, false);

            InventorySyncRunResponse mutationRun = submitScheduledAndAwait("oracle-mutation");
            assertThat(mutationRun.status()).isEqualTo("SUCCEEDED");
            assertThat(mutationRun.errorCount()).isZero();
            assertThat(mutationRun.readCount()).isEqualTo(1);
            assertThat(mutationRun.mappedCount()).isEqualTo(1);
            assertThat(mutationRun.changedCount()).isGreaterThanOrEqualTo(6);
            assertThat(selectCanonical(original)).isEqualTo(mutated.toCanonicalSnapshot());
            assertRiskLineage(original.inventoryBalanceId(), mutationRun.syncRunId());
            assertThat(activeSkuCostCount()).isZero();
        } finally {
            applySourceValues(original, true);

            InventorySyncRunResponse restoreRun = submitScheduledAndAwait("oracle-restore");
            assertThat(restoreRun.status()).isEqualTo("SUCCEEDED");
            assertThat(restoreRun.errorCount()).isZero();
            assertThat(restoreRun.readCount()).isEqualTo(1);
            assertThat(restoreRun.mappedCount()).isEqualTo(1);
            assertThat(restoreRun.changedCount()).isGreaterThanOrEqualTo(6);
            assertThat(selectCanonical(original)).isEqualTo(canonicalBefore);
            assertRiskLineage(original.inventoryBalanceId(), restoreRun.syncRunId());
            assertThat(pendingOfflineCount()).isZero();
        }
    }

    private SourceFixture selectFixture() {
        return jdbcTemplate.queryForObject("""
                SELECT m.source_record_key, m.product_id, p.category_id, m.sku_id, m.lot_id,
                       m.inventory_balance_id, m.sku_channel_price_id, m.inventory_policy_id,
                       src.category_small_name, src.item_name, src.branch_option_name,
                       src.tag_price_amount, src.store_sale_price_amount, src.store_unit_cost_amount,
                       TO_DATE(src.made_date_text, 'YYYY-MM-DD') AS manufactured_date,
                       src.safe_stock_count, src.target_stock_count, src.available_stock_count,
                       RTRIM(src.record_hash) AS record_hash, src.row_version
                  FROM inventory_source_row_map m
                  JOIN inventory_source_offline src
                    ON src.source_record_key = m.source_record_key
                  JOIN product p ON p.product_id = m.product_id AND p.is_deleted = 0
                 WHERE m.source_type = 'OFFLINE'
                   AND m.mapping_status = 'MAPPED'
                   AND m.sku_channel_price_id IS NOT NULL
                   AND m.inventory_policy_id IS NOT NULL
                   AND m.sku_cost_id IS NULL
                   AND src.category_small_name IS NOT NULL
                   AND src.item_name IS NOT NULL
                   AND src.branch_option_name IS NOT NULL
                   AND src.tag_price_amount IS NOT NULL
                   AND src.store_sale_price_amount IS NOT NULL
                   AND src.store_unit_cost_amount IS NOT NULL
                   AND src.made_date_text IS NOT NULL
                   AND src.safe_stock_count IS NOT NULL
                   AND src.target_stock_count IS NOT NULL
                   AND src.active_yn = 'Y' AND src.is_deleted = 0
                   AND m.is_deleted = 0
                 ORDER BY m.source_record_key
                 FETCH FIRST 1 ROW ONLY
                """, (rs, rowNum) -> new SourceFixture(
                rs.getString("source_record_key"),
                rs.getLong("product_id"),
                rs.getLong("category_id"),
                rs.getLong("sku_id"),
                rs.getLong("lot_id"),
                rs.getLong("inventory_balance_id"),
                rs.getLong("sku_channel_price_id"),
                rs.getLong("inventory_policy_id"),
                rs.getString("category_small_name"),
                rs.getString("item_name"),
                rs.getString("branch_option_name"),
                rs.getBigDecimal("tag_price_amount"),
                rs.getBigDecimal("store_sale_price_amount"),
                rs.getBigDecimal("store_unit_cost_amount"),
                rs.getObject("manufactured_date", LocalDate.class),
                rs.getBigDecimal("safe_stock_count"),
                rs.getBigDecimal("target_stock_count"),
                rs.getBigDecimal("available_stock_count"),
                rs.getString("record_hash"),
                rs.getLong("row_version")
        ));
    }

    private void applySourceValues(SourceFixture fixture, boolean restoreHash) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            int updated = jdbcTemplate.update("""
                    UPDATE inventory_source_offline
                       SET category_small_name = ?, item_name = ?, branch_option_name = ?,
                           tag_price_amount = ?, store_sale_price_amount = ?, store_unit_cost_amount = ?,
                           made_date_text = TO_CHAR(?, 'YYYY-MM-DD'),
                           safe_stock_count = ?, target_stock_count = ?, available_stock_count = ?,
                           record_hash = CASE WHEN ? = 1 THEN ?
                                              ELSE RAWTOHEX(STANDARD_HASH(RTRIM(record_hash) || ':ORACLE_MUTATION_E2E', 'SHA256')) END,
                           row_version = row_version + 1,
                           synced_record_hash = NULL, last_sync_run_id = NULL, last_synced_at = NULL,
                           source_modified_at = SYSTIMESTAMP, updated_at = SYSTIMESTAMP
                     WHERE source_record_key = ? AND active_yn = 'Y' AND is_deleted = 0
                    """,
                    fixture.categoryName(), fixture.productName(), fixture.skuName(),
                    fixture.sellingPrice(), fixture.actualPrice(), fixture.productCost(),
                    java.sql.Date.valueOf(fixture.manufacturedDate()),
                    fixture.safetyStockQty(), fixture.targetStockQty(), fixture.onHandQty(),
                    restoreHash ? 1 : 0, fixture.recordHash(), fixture.sourceRecordKey());
            assertThat(updated).isEqualTo(1);
            updated = jdbcTemplate.update("""
                    UPDATE inventory_source_state
                       SET current_version = current_version + 1,
                           pending_record_count = (
                               SELECT COUNT(*) FROM inventory_source_offline
                                WHERE active_yn = 'Y' AND is_deleted = 0
                                  AND (synced_record_hash IS NULL OR record_hash != synced_record_hash)
                           ),
                           last_changed_at = SYSTIMESTAMP, updated_at = SYSTIMESTAMP
                     WHERE source_type = 'OFFLINE'
                    """);
            assertThat(updated).isEqualTo(1);
        });
    }

    private InventorySyncRunResponse submitScheduledAndAwait(String prefix) throws InterruptedException {
        String clientRequestId = prefix + "-" + System.currentTimeMillis();
        var submitted = submissionService.submitScheduled(new InventorySyncStartRequest(clientRequestId));
        assertThat(submitted.httpStatus()).isEqualTo(202);
        assertThat(submitted.response()).isNotNull();
        InventorySyncRunResponse terminal = awaitTerminal(submitted.response().syncRunId(), Duration.ofMinutes(3));
        awaitBatchMetadataTerminal(terminal.syncRunId(), Duration.ofSeconds(10));
        return terminal;
    }

    private void awaitBatchMetadataTerminal(Long runId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        String status = batchStatus(runId);
        while ((status == null || !Set.of("COMPLETED", "FAILED", "STOPPED", "ABANDONED").contains(status))
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
            status = batchStatus(runId);
        }
        assertThat(status).as("Spring Batch metadata must finish before the test context closes")
                .isIn("COMPLETED", "FAILED", "STOPPED", "ABANDONED");
    }

    private String batchStatus(Long runId) {
        return jdbcTemplate.query("""
                SELECT batch.status
                  FROM inventory_sync_run run
                  JOIN batch_job_execution batch
                    ON batch.job_execution_id = run.main_batch_job_execution_id
                 WHERE run.inventory_sync_run_id = ?
                """, rs -> rs.next() ? rs.getString(1) : null, runId);
    }

    private InventorySyncRunResponse awaitTerminal(Long runId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        InventorySyncRunResponse response = submissionService.get(runId);
        while (response != null && !TERMINAL_STATUSES.contains(response.status()) && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
            response = submissionService.get(runId);
        }
        assertThat(response).as("durable sync run must remain queryable").isNotNull();
        assertThat(TERMINAL_STATUSES).as("sync must reach a terminal state before timeout")
                .contains(response.status());
        return response;
    }

    private CanonicalSnapshot selectCanonical(SourceFixture fixture) {
        return jdbcTemplate.queryForObject("""
                SELECT p.category_id, p.product_name, s.sku_name,
                       price.selling_price, price.actual_price, price.product_cost,
                       lot.manufactured_date, policy.safety_stock_qty, policy.target_stock_qty,
                       balance.on_hand_qty
                  FROM product p
                  JOIN sku s ON s.sku_id = ? AND s.product_id = p.product_id
                  JOIN sku_channel_price price ON price.sku_channel_price_id = ?
                  JOIN lot ON lot.lot_id = ?
                  JOIN inventory_policy policy ON policy.inventory_policy_id = ?
                  JOIN inventory_balance balance ON balance.inventory_balance_id = ?
                 WHERE p.product_id = ?
                """, (rs, rowNum) -> new CanonicalSnapshot(
                rs.getLong("category_id"),
                rs.getString("product_name"),
                rs.getString("sku_name"),
                rs.getBigDecimal("selling_price"),
                rs.getBigDecimal("actual_price"),
                rs.getBigDecimal("product_cost"),
                rs.getObject("manufactured_date", LocalDate.class),
                rs.getBigDecimal("safety_stock_qty"),
                rs.getBigDecimal("target_stock_qty"),
                rs.getBigDecimal("on_hand_qty")
        ), fixture.skuId(), fixture.skuChannelPriceId(), fixture.lotId(), fixture.inventoryPolicyId(),
                fixture.inventoryBalanceId(), fixture.productId());
    }

    private AlternativeCategory selectAlternativeCategory(Long currentCategoryId) {
        return jdbcTemplate.queryForObject("""
                SELECT category_id, category_name FROM category
                 WHERE is_deleted = 0 AND category_id != ?
                 ORDER BY category_id
                 FETCH FIRST 1 ROW ONLY
                """, (rs, rowNum) -> new AlternativeCategory(
                rs.getLong("category_id"), rs.getString("category_name")
        ), currentCategoryId);
    }

    private void assertRiskLineage(Long inventoryBalanceId, Long runId) {
        Long actual = jdbcTemplate.queryForObject("""
                SELECT inventory_sync_run_id FROM risk_assessment
                 WHERE inventory_balance_id = ? AND is_deleted = 0
                 FETCH FIRST 1 ROW ONLY
                """, Long.class, inventoryBalanceId);
        assertThat(actual).isEqualTo(runId);
    }

    private long activeSkuCostCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sku_cost WHERE is_deleted = 0", Long.class);
        return count == null ? 0 : count;
    }

    private long pendingOfflineCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT pending_record_count FROM inventory_source_state WHERE source_type = 'OFFLINE'
                """, Long.class);
        return count == null ? 0 : count;
    }

    private record CanonicalSnapshot(
            Long categoryId,
            String productName,
            String skuName,
            BigDecimal sellingPrice,
            BigDecimal actualPrice,
            BigDecimal productCost,
            LocalDate manufacturedDate,
            BigDecimal safetyStockQty,
            BigDecimal targetStockQty,
            BigDecimal onHandQty
    ) { }

    private record SourceFixture(
            String sourceRecordKey,
            Long productId,
            Long categoryId,
            Long skuId,
            Long lotId,
            Long inventoryBalanceId,
            Long skuChannelPriceId,
            Long inventoryPolicyId,
            String categoryName,
            String productName,
            String skuName,
            BigDecimal sellingPrice,
            BigDecimal actualPrice,
            BigDecimal productCost,
            LocalDate manufacturedDate,
            BigDecimal safetyStockQty,
            BigDecimal targetStockQty,
            BigDecimal onHandQty,
            String recordHash,
            long rowVersion
    ) {
        SourceFixture mutated(AlternativeCategory alternativeCategory) {
            return new SourceFixture(
                    sourceRecordKey, productId, alternativeCategory.categoryId(), skuId, lotId, inventoryBalanceId,
                    skuChannelPriceId, inventoryPolicyId, alternativeCategory.categoryName(),
                    productName + " [SYNC-E2E]", skuName + " [SYNC-E2E]",
                    sellingPrice.add(BigDecimal.TEN), actualPrice.add(BigDecimal.TEN),
                    productCost.add(BigDecimal.TEN), manufacturedDate.plusDays(1),
                    safetyStockQty.add(BigDecimal.ONE), targetStockQty.add(BigDecimal.ONE),
                    onHandQty.add(BigDecimal.ONE), recordHash, rowVersion
            );
        }

        CanonicalSnapshot toCanonicalSnapshot() {
            return new CanonicalSnapshot(categoryId, productName, skuName, sellingPrice, actualPrice, productCost,
                    manufacturedDate, safetyStockQty, targetStockQty, onHandQty);
        }
    }

    private record AlternativeCategory(Long categoryId, String categoryName) { }
}
