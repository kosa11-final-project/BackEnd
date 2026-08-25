package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.stockit.backend.feature.inventorysync.dto.InventorySyncRunResponse;
import com.stockit.backend.feature.inventorysync.dto.InventorySyncStartRequest;
import com.stockit.backend.feature.inventorysync.service.InventorySyncSubmissionService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INVENTORY_SYNC_ORACLE_E2E", matches = "true")
class InventorySourceSyncOracleEndToEndTest {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED", "FAILED", "SOURCE_CHANGED", "CANCELLED"
    );

    @Autowired
    private InventorySyncSubmissionService submissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void buttonSubmissionCompletesWithoutChangingTheCanonicalBaseline() throws InterruptedException {
        CanonicalTotals before = canonicalTotals();
        long pendingBefore = pendingSourceCount();
        Long actorId = jdbcTemplate.queryForObject(
                "SELECT MIN(user_id) FROM app_user WHERE login_id = 'gf0001' AND active_yn = 'Y'",
                Long.class
        );

        String clientRequestId = "oracle-e2e-" + System.currentTimeMillis();
        var submitted = submissionService.submit(new InventorySyncStartRequest(clientRequestId), actorId);

        assertThat(submitted.httpStatus()).isEqualTo(202);
        assertThat(submitted.response()).isNotNull();
        InventorySyncRunResponse terminal = awaitTerminal(submitted.response().syncRunId(), Duration.ofMinutes(3));

        assertThat(terminal.status()).isEqualTo("SUCCEEDED");
        assertThat(terminal.errorCount()).isZero();
        assertThat(terminal.changedCount()).isZero();
        assertThat(terminal.readCount()).isEqualTo(pendingBefore);
        assertThat(terminal.mappedCount()).isEqualTo(pendingBefore);
        assertThat(terminal.sourceStates()).hasSize(4)
                .allSatisfy(state -> assertThat(state.pendingRecordCount()).isZero());
        assertThat(canonicalTotals()).isEqualTo(before);
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

    private CanonicalTotals canonicalTotals() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS row_count,
                       NVL(SUM(on_hand_qty), 0) AS on_hand_qty,
                       NVL(SUM(reserved_qty), 0) AS reserved_qty
                  FROM inventory_balance
                 WHERE is_deleted = 0
                """, (resultSet, rowNumber) -> new CanonicalTotals(
                resultSet.getLong("row_count"),
                resultSet.getBigDecimal("on_hand_qty"),
                resultSet.getBigDecimal("reserved_qty")
        ));
    }

    private long pendingSourceCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT NVL(SUM(pending_record_count), 0) FROM inventory_source_state",
                Long.class
        );
        return count == null ? 0 : count;
    }

    private record CanonicalTotals(long rowCount, BigDecimal onHandQty, BigDecimal reservedQty) { }
}
