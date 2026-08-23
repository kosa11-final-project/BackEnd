package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsDemoBackfillResponse;
import com.stockit.backend.feature.statistics.service.StrategyStatisticsDemoBackfillService;

@Service
public class StrategyStatisticsDemoBackfillServiceImpl implements StrategyStatisticsDemoBackfillService {
    private static final int MAX_DAYS = 366;
    private static final Map<String, Object> NO_PARAMETERS = Map.of();
    private static final LocalDate DEFAULT_FROM_DATE = LocalDate.of(2025, 8, 24);
    private static final LocalDate DEFAULT_TO_DATE = LocalDate.of(2026, 8, 23);

    private static final String INSERT_CASE = """
            INSERT INTO strategy_case (
                strategy_case_id, sku_id, created_by, updated_by, requested_sales_point_id,
                case_code, case_name, case_status, request_payload_json, completed_at,
                created_at, updated_at, is_deleted
            ) VALUES (
                :caseId, :skuId, :ownerUserId, :ownerUserId, :requestedSalesPointId,
                :caseCode, :caseName, 'EXECUTION_COMPLETED', :requestPayloadJson, :completedAt,
                :createdAt, :completedAt, 0
            )
            """;
    private static final String INSERT_OPTION = """
            INSERT INTO strategy_option (
                strategy_option_id, strategy_case_id, option_rank, option_name,
                recommendation_reason, advantage_text, caution_text,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :optionId, :caseId, 1, :optionName,
                :recommendationReason, :advantageText, :cautionText,
                :createdAt, :completedAt, :ownerUserId, :ownerUserId, 0
            )
            """;
    private static final String INSERT_SIMULATION = """
            INSERT INTO strategy_simulation (
                simulation_id, strategy_option_id, input_source_type, target_quantity,
                expected_sales_qty, expected_remaining_qty, avoided_disposal_cost,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :simulationId, :optionId, 'AI_RECOMMENDED', :goalTargetValue,
                :goalTargetValue, 0, :estimatedLossSavingsAmount,
                :createdAt, :completedAt, :ownerUserId, :ownerUserId, 0
            )
            """;
    private static final String INSERT_ACTION = """
            INSERT INTO strategy_action (
                strategy_action_id, strategy_option_id, source_sales_point_id, target_sales_point_id,
                source_warehouse_id, destination_warehouse_id, action_type,
                action_quantity, strategy_price, discount_rate, start_date, end_date,
                estimated_action_cost, action_order,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :actionId, :optionId, :sourceSalesPointId, :targetSalesPointId,
                :sourceWarehouseId, :destinationWarehouseId, :actionType,
                :actionQuantity, :strategyPrice, :discountRate, :startDate, :endDate,
                :estimatedActionCost, :actionOrder,
                :createdAt, :completedAt, :ownerUserId, :ownerUserId, 0
            )
            """;
    private static final String INSERT_INVENTORY_SNAPSHOT = """
            INSERT INTO strategy_inventory_snapshot (
                inventory_snapshot_id, strategy_case_id, sku_id, lot_id, sales_point_id,
                inventory_balance_id, on_total_qty, on_hand_qty, safety_stock_qty,
                daily_sales_velocity, forecast_qty, expiry_date, warehouse_id,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :inventorySnapshotId, :caseId, :skuId, :lotId, :salesPointId,
                :inventoryBalanceId, :onTotalQty, :onHandQty, :safetyStockQty,
                :dailySalesVelocity, :forecastQty, :expiryDate, :warehouseId,
                :createdAt, :completedAt, :ownerUserId, :ownerUserId, 0
            )
            """;
    private static final String INSERT_PERFORMANCE = """
            INSERT INTO strategy_performance (
                strategy_performance_id, strategy_option_id, performance_date,
                actual_sales_qty, actual_revenue, actual_contribution_margin,
                actual_remaining_qty, moved_quantity, disposed_quantity, achievement_rate,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :performanceId, :optionId, :performanceDate,
                :actualSalesQty, :actualRevenue, :actualContributionMargin,
                :actualRemainingQty, :movedQuantity, :disposedQuantity, :performanceAchievementRate,
                :createdAt, :completedAt, :ownerUserId, :ownerUserId, 0
            )
            """;
    private static final String INSERT_SELECTION = """
            INSERT INTO final_strategy_selection (
                final_selection_id, strategy_case_id, strategy_option_id, last_synced_at,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :selectionId, :caseId, :optionId, :completedAt,
                :createdAt, :completedAt, :ownerUserId, :ownerUserId, 0
            )
            """;
    private static final String INSERT_RESULT = """
            INSERT INTO strategy_execution_result (
                strategy_execution_result_id, final_selection_id, result_status,
                planned_start_date, planned_end_date, goal_metric_code,
                goal_target_value, goal_actual_value, achievement_rate,
                start_risk_stock_qty, end_risk_stock_qty,
                start_expected_disposal_qty, end_expected_disposal_qty,
                start_unit_cost, estimated_loss_savings_amount, finalized_sync_run_id,
                calculation_version, start_captured_at, completed_at,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :resultId, :selectionId, 'COMPLETED',
                :startDate, :endDate, 'SALES_QTY',
                :goalTargetValue, :goalActualValue, :achievementRate,
                :startRiskStockQty, :endRiskStockQty,
                :startExpectedDisposalQty, :endExpectedDisposalQty,
                :startUnitCost, :estimatedLossSavingsAmount, :finalizedSyncRunId,
                'SALES_ONLY_V1', :createdAt, :completedAt,
                :createdAt, :completedAt, :ownerUserId, :ownerUserId, 0
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final StrategyStatisticsDemoDataFactory dataFactory;
    private final ObjectMapper objectMapper;

    public StrategyStatisticsDemoBackfillServiceImpl(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataFactory = new StrategyStatisticsDemoDataFactory();
    }

    @Override
    @Transactional
    public StrategyStatisticsDemoBackfillResponse backfill(LocalDate fromDate, LocalDate toDate) {
        LocalDate resolvedTo = toDate == null ? DEFAULT_TO_DATE : toDate;
        LocalDate resolvedFrom = fromDate == null ? DEFAULT_FROM_DATE : fromDate;
        validateRange(resolvedFrom, resolvedTo);
        ensureExecutionResultTableExists();

        ensureNoExternalDemoReferences();
        StrategyStatisticsDemoDimensions dimensions = loadDimensions(resolvedFrom, resolvedTo);
        List<StrategyStatisticsDemoData> requested = dataFactory.create(
                resolvedFrom,
                resolvedTo,
                dimensions
        );
        deleteExistingDemoStrategies();
        insertCases(requested);
        insertOptions(requested);
        insertSimulations(requested);
        int actionCount = insertActions(requested);
        insertInventorySnapshots(requested);
        insertPerformance(requested);
        insertSelections(requested);
        insertResults(requested);
        validateInsertedLedger();
        return response(resolvedFrom, resolvedTo, requested.size(), requested.size(), actionCount);
    }

    private StrategyStatisticsDemoDimensions loadDimensions(LocalDate fromDate, LocalDate toDate) {
        List<StrategyStatisticsDemoSalesCandidate> sales = selectSalesCandidates(fromDate, toDate);
        List<StrategyStatisticsDemoInventoryCandidate> inventories = jdbcTemplate.query(
                """
                        WITH sales_velocity AS (
                            SELECT sales.sku_id, sales.sales_point_id, AVG(sales.net_sales_qty) AS daily_sales_velocity
                            FROM sales_daily sales
                            WHERE sales.sales_date BETWEEN :fromDate AND :toDate
                              AND sales.is_deleted = 0
                            GROUP BY sales.sku_id, sales.sales_point_id
                        ), policy_summary AS (
                            SELECT policy.sku_id, policy.warehouse_id,
                                   COALESCE(policy.allocated_sales_point_id, policy.stock_sales_point_id, -1) AS sales_point_id,
                                   MAX(policy.safety_stock_qty) AS safety_stock_qty
                            FROM inventory_policy policy
                            WHERE policy.is_deleted = 0
                            GROUP BY policy.sku_id, policy.warehouse_id,
                                     COALESCE(policy.allocated_sales_point_id, policy.stock_sales_point_id, -1)
                        )
                        SELECT balance.inventory_balance_id, balance.sku_id, balance.lot_id,
                               COALESCE(balance.allocated_sales_point_id, balance.stock_sales_point_id) AS sales_point_id,
                               balance.warehouse_id, balance.total_qty, balance.on_hand_qty,
                               COALESCE(policy_summary.safety_stock_qty, 0) AS safety_stock_qty,
                               COALESCE(sales_velocity.daily_sales_velocity, 0) AS daily_sales_velocity,
                               CAST(NULL AS NUMBER) AS forecast_qty,
                               lot.expiry_date
                        FROM inventory_balance balance
                        JOIN sku ON sku.sku_id = balance.sku_id
                          AND sku.active_yn = 'Y' AND sku.is_deleted = 0
                        JOIN lot ON lot.lot_id = balance.lot_id
                          AND lot.sku_id = balance.sku_id AND lot.is_deleted = 0
                        JOIN warehouse ON warehouse.warehouse_id = balance.warehouse_id
                          AND warehouse.active_yn = 'Y' AND warehouse.is_deleted = 0
                        JOIN sales_point point
                          ON point.sales_point_id = COALESCE(
                              balance.allocated_sales_point_id, balance.stock_sales_point_id)
                         AND point.active_yn = 'Y' AND point.is_deleted = 0
                        LEFT JOIN policy_summary
                          ON policy_summary.sku_id = balance.sku_id
                         AND policy_summary.warehouse_id = balance.warehouse_id
                         AND policy_summary.sales_point_id = COALESCE(
                             balance.allocated_sales_point_id, balance.stock_sales_point_id, -1)
                        JOIN sales_velocity
                          ON sales_velocity.sku_id = balance.sku_id
                         AND sales_velocity.sales_point_id = COALESCE(
                             balance.allocated_sales_point_id, balance.stock_sales_point_id)
                        WHERE balance.is_deleted = 0
                          AND balance.on_hand_qty > 0
                        ORDER BY balance.sku_id, balance.warehouse_id, sales_point_id,
                                 balance.inventory_balance_id
                        """,
                Map.of("fromDate", fromDate, "toDate", toDate),
                (resultSet, rowNum) -> new StrategyStatisticsDemoInventoryCandidate(
                        resultSet.getLong("inventory_balance_id"), resultSet.getLong("sku_id"),
                        resultSet.getLong("lot_id"), nullableLong(resultSet, "sales_point_id"),
                        resultSet.getLong("warehouse_id"), resultSet.getBigDecimal("total_qty"),
                        resultSet.getBigDecimal("on_hand_qty"), resultSet.getBigDecimal("safety_stock_qty"),
                        resultSet.getBigDecimal("daily_sales_velocity"), resultSet.getBigDecimal("forecast_qty"),
                        resultSet.getObject("expiry_date", LocalDate.class))
        );
        List<Long> ownerIds = jdbcTemplate.queryForList(
                """
                        SELECT user_id FROM app_user
                        WHERE active_yn = 'Y' AND is_deleted = 0 AND login_id <> '__system__'
                        ORDER BY user_id FETCH FIRST 1 ROW ONLY
                        """,
                NO_PARAMETERS,
                Long.class
        );
        List<Long> syncRunIds = jdbcTemplate.queryForList(
                """
                        SELECT inventory_sync_run_id
                        FROM (
                            SELECT inventory_sync_run_id
                            FROM inventory_sync_run
                            WHERE run_status = 'SUCCEEDED'
                            ORDER BY completed_at DESC NULLS LAST, inventory_sync_run_id DESC
                        )
                        WHERE ROWNUM = 1
                        """,
                NO_PARAMETERS,
                Long.class
        );
        if (sales.isEmpty() || inventories.isEmpty() || ownerIds.isEmpty() || syncRunIds.isEmpty()) {
            throw new IllegalStateException(
                    "AI 전략 데모 이력 생성에 필요한 실제 판매, 유효 재고, 사용자 또는 성공 동기화가 없습니다."
            );
        }
        return new StrategyStatisticsDemoDimensions(sales, inventories, ownerIds.get(0), syncRunIds.get(0));
    }

    private List<StrategyStatisticsDemoSalesCandidate> selectSalesCandidates(LocalDate fromDate, LocalDate toDate) {
        Map<String, StrategyStatisticsDemoSalesCandidateBuilder> grouped = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        WITH valid_inventory AS (
                            SELECT DISTINCT balance.sku_id,
                                   COALESCE(balance.allocated_sales_point_id, balance.stock_sales_point_id) AS sales_point_id
                            FROM inventory_balance balance
                            WHERE balance.lot_id IS NOT NULL
                              AND balance.warehouse_id IS NOT NULL
                              AND balance.on_hand_qty > 0
                              AND balance.is_deleted = 0
                        ), eligible_pairs AS (
                            SELECT sales.sku_id, sales.sales_point_id
                            FROM sales_daily sales
                            JOIN valid_inventory
                              ON valid_inventory.sku_id = sales.sku_id
                             AND valid_inventory.sales_point_id = sales.sales_point_id
                            WHERE sales.sales_date BETWEEN :fromDate AND :toDate
                              AND sales.net_sales_qty > 0
                              AND sales.is_deleted = 0
                            GROUP BY sales.sku_id, sales.sales_point_id
                            ORDER BY SUM(sales.net_sales_qty) DESC,
                                     sales.sku_id, sales.sales_point_id
                            FETCH FIRST 1000 ROWS ONLY
                        ), latest_price AS (
                            SELECT sku_id, sales_point_id, product_cost, actual_price
                            FROM (
                                SELECT price.sku_id, price.sales_point_id, price.product_cost, price.actual_price,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY price.sku_id, price.sales_point_id
                                           ORDER BY price.effective_from DESC, price.sku_channel_price_id DESC
                                       ) AS row_no
                                FROM sku_channel_price price
                                WHERE price.is_deleted = 0
                            )
                            WHERE row_no = 1
                        )
                        SELECT sales.sku_id, sales.sales_point_id, sales.sales_date,
                               sales.net_sales_qty, sales.net_sales_amount,
                               COALESCE(latest_price.product_cost, 0) AS unit_cost,
                               COALESCE(latest_price.actual_price, CASE WHEN sales.net_sales_qty > 0
                                      THEN sales.net_sales_amount / sales.net_sales_qty ELSE 0 END) AS selling_price
                        FROM sales_daily sales
                        JOIN eligible_pairs
                          ON eligible_pairs.sku_id = sales.sku_id
                         AND eligible_pairs.sales_point_id = sales.sales_point_id
                        JOIN sku ON sku.sku_id = sales.sku_id
                          AND sku.active_yn = 'Y' AND sku.is_deleted = 0
                        JOIN sales_point point ON point.sales_point_id = sales.sales_point_id
                          AND point.active_yn = 'Y' AND point.is_deleted = 0
                        LEFT JOIN latest_price
                          ON latest_price.sku_id = sales.sku_id
                         AND latest_price.sales_point_id = sales.sales_point_id
                        WHERE sales.sales_date BETWEEN :fromDate AND :toDate
                          AND sales.is_deleted = 0
                        ORDER BY sales.sku_id, sales.sales_point_id, sales.sales_date
                        """,
                Map.of("fromDate", fromDate, "toDate", toDate),
                resultSet -> {
                    long skuId = resultSet.getLong("sku_id");
                    long pointId = resultSet.getLong("sales_point_id");
                    BigDecimal unitCost = resultSet.getBigDecimal("unit_cost");
                    BigDecimal sellingPrice = resultSet.getBigDecimal("selling_price");
                    String key = skuId + ":" + pointId;
                    StrategyStatisticsDemoSalesCandidateBuilder builder = grouped.computeIfAbsent(key,
                            ignored -> new StrategyStatisticsDemoSalesCandidateBuilder(
                                    skuId, pointId, unitCost, sellingPrice));
                    LocalDate date = resultSet.getObject("sales_date", LocalDate.class);
                    builder.dailySales().put(date, new StrategyStatisticsDemoSalesDay(date,
                            resultSet.getBigDecimal("net_sales_qty"),
                            resultSet.getBigDecimal("net_sales_amount")));
                });
        return grouped.values().stream().map(StrategyStatisticsDemoSalesCandidateBuilder::build).toList();
    }

    private void ensureExecutionResultTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'STRATEGY_EXECUTION_RESULT'",
                NO_PARAMETERS,
                Integer.class
        );
        if (count == null || count == 0) {
            throw new IllegalStateException("V26 전략 실행 결과 마이그레이션 적용 후 데모 이력을 생성할 수 있습니다.");
        }
    }

    private void insertCases(List<StrategyStatisticsDemoData> values) {
        jdbcTemplate.batchUpdate(INSERT_CASE, parameters(values));
    }

    private void insertOptions(List<StrategyStatisticsDemoData> values) {
        SqlParameterSource[] parameters = values.stream()
                .map(this::commonParameters)
                .map(value -> value
                        .addValue("optionName", "AI 추천 실행안")
                        .addValue("recommendationReason", "통계 화면 검증용으로 재현 가능한 성과 흐름을 구성했습니다.")
                        .addValue("advantageText", "위험재고와 예상 폐기량을 함께 줄이는 조합입니다.")
                        .addValue("cautionText", "데모 데이터이며 실제 전략 의사결정에는 사용하지 않습니다."))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_OPTION, parameters);
    }

    private void insertSimulations(List<StrategyStatisticsDemoData> values) {
        jdbcTemplate.batchUpdate(INSERT_SIMULATION, parameters(values));
    }

    private int insertActions(List<StrategyStatisticsDemoData> values) {
        List<SqlParameterSource> parameters = new ArrayList<>();
        for (StrategyStatisticsDemoData value : values) {
            for (StrategyStatisticsDemoAction action : value.actions()) {
                parameters.add(commonParameters(value)
                        .addValue("actionId", action.actionId())
                        .addValue("actionType", action.actionType())
                        .addValue("sourceSalesPointId", action.sourceSalesPointId())
                        .addValue("targetSalesPointId", action.targetSalesPointId())
                        .addValue("sourceWarehouseId", action.sourceWarehouseId())
                        .addValue("destinationWarehouseId", action.destinationWarehouseId())
                        .addValue("actionQuantity", action.actionQuantity())
                        .addValue("strategyPrice", action.strategyPrice())
                        .addValue("discountRate", action.discountRate())
                        .addValue("estimatedActionCost", action.estimatedActionCost())
                        .addValue("actionOrder", action.actionOrder()));
            }
        }
        jdbcTemplate.batchUpdate(INSERT_ACTION, parameters.toArray(SqlParameterSource[]::new));
        return parameters.size();
    }

    private void insertInventorySnapshots(List<StrategyStatisticsDemoData> values) {
        List<SqlParameterSource> parameters = new ArrayList<>();
        for (StrategyStatisticsDemoData value : values) {
            for (StrategyStatisticsDemoInventorySnapshot snapshot : value.inventorySnapshots()) {
                parameters.add(commonParameters(value)
                        .addValue("inventorySnapshotId", snapshot.inventorySnapshotId())
                        .addValue("inventoryBalanceId", snapshot.inventoryBalanceId())
                        .addValue("lotId", snapshot.lotId())
                        .addValue("salesPointId", snapshot.salesPointId())
                        .addValue("warehouseId", snapshot.warehouseId())
                        .addValue("onTotalQty", snapshot.onTotalQty())
                        .addValue("onHandQty", snapshot.onHandQty())
                        .addValue("safetyStockQty", snapshot.safetyStockQty())
                        .addValue("dailySalesVelocity", snapshot.dailySalesVelocity())
                        .addValue("forecastQty", snapshot.forecastQty())
                        .addValue("expiryDate", snapshot.expiryDate()));
            }
        }
        jdbcTemplate.batchUpdate(INSERT_INVENTORY_SNAPSHOT, parameters.toArray(SqlParameterSource[]::new));
    }

    private void insertPerformance(List<StrategyStatisticsDemoData> values) {
        List<SqlParameterSource> parameters = new ArrayList<>();
        for (StrategyStatisticsDemoData value : values) {
            for (StrategyStatisticsDemoPerformance performance : value.performance()) {
                parameters.add(commonParameters(value)
                        .addValue("performanceId", performance.performanceId())
                        .addValue("performanceDate", performance.performanceDate())
                        .addValue("actualSalesQty", performance.actualSalesQty())
                        .addValue("actualRevenue", performance.actualRevenue())
                        .addValue("actualContributionMargin", performance.actualContributionMargin())
                        .addValue("actualRemainingQty", performance.actualRemainingQty())
                        .addValue("movedQuantity", performance.movedQuantity())
                        .addValue("disposedQuantity", performance.disposedQuantity())
                        .addValue("performanceAchievementRate", performance.achievementRate()));
            }
        }
        jdbcTemplate.batchUpdate(INSERT_PERFORMANCE, parameters.toArray(SqlParameterSource[]::new));
    }

    private void insertSelections(List<StrategyStatisticsDemoData> values) {
        jdbcTemplate.batchUpdate(INSERT_SELECTION, parameters(values));
    }

    private void insertResults(List<StrategyStatisticsDemoData> values) {
        jdbcTemplate.batchUpdate(INSERT_RESULT, parameters(values));
    }

    private SqlParameterSource[] parameters(List<StrategyStatisticsDemoData> values) {
        return values.stream().map(this::commonParameters).toArray(SqlParameterSource[]::new);
    }

    private MapSqlParameterSource commonParameters(StrategyStatisticsDemoData value) {
        return new MapSqlParameterSource()
                .addValue("caseId", value.caseId())
                .addValue("optionId", value.optionId())
                .addValue("simulationId", value.simulationId())
                .addValue("selectionId", value.selectionId())
                .addValue("resultId", value.resultId())
                .addValue("skuId", value.skuId())
                .addValue("ownerUserId", value.ownerUserId())
                .addValue("finalizedSyncRunId", value.finalizedSyncRunId())
                .addValue("requestedSalesPointId", value.requestedSalesPointId())
                .addValue("caseCode", value.caseCode())
                .addValue("caseName", value.caseName())
                .addValue("requestPayloadJson", requestPayload(value))
                .addValue("startDate", value.startDate())
                .addValue("endDate", value.endDate())
                .addValue("createdAt", value.createdAt())
                .addValue("completedAt", value.completedAt())
                .addValue("goalTargetValue", value.goalTargetValue())
                .addValue("goalActualValue", value.goalActualValue())
                .addValue("achievementRate", value.achievementRate())
                .addValue("startRiskStockQty", value.startRiskStockQty())
                .addValue("endRiskStockQty", value.endRiskStockQty())
                .addValue("startExpectedDisposalQty", value.startExpectedDisposalQty())
                .addValue("endExpectedDisposalQty", value.endExpectedDisposalQty())
                .addValue("startUnitCost", value.startUnitCost())
                .addValue("estimatedLossSavingsAmount", value.estimatedLossSavingsAmount());
    }

    private String requestPayload(StrategyStatisticsDemoData value) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "demo", true,
                    "source", "statistics-demo-rebuild",
                    "salesSource", "sales_daily",
                    "strategyCaseId", value.caseId()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 전략 데모 요청 JSON 생성에 실패했습니다.", exception);
        }
    }

    private void ensureNoExternalDemoReferences() {
        Integer notifications = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM notification notification
                        JOIN strategy_case strategy_case
                          ON strategy_case.strategy_case_id = notification.strategy_case_id
                        WHERE strategy_case.case_code LIKE 'DEMO-STAT-%'
                        """,
                NO_PARAMETERS,
                Integer.class
        );
        if (notifications != null && notifications > 0) {
            throw new IllegalStateException("DEMO-STAT 전략을 참조하는 알림이 있어 허용 범위 안에서 안전하게 교체할 수 없습니다.");
        }
    }

    private void deleteExistingDemoStrategies() {
        List<String> statements = List.of(
                """
                DELETE FROM strategy_execution_result WHERE final_selection_id IN (
                       SELECT selection.final_selection_id FROM final_strategy_selection selection
                       JOIN strategy_case strategy_case ON strategy_case.strategy_case_id = selection.strategy_case_id
                       WHERE strategy_case.case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_lot_allocation WHERE strategy_action_id IN (
                       SELECT action.strategy_action_id FROM strategy_action action
                       JOIN strategy_option option_value ON option_value.strategy_option_id = action.strategy_option_id
                       JOIN strategy_case strategy_case ON strategy_case.strategy_case_id = option_value.strategy_case_id
                       WHERE strategy_case.case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_performance WHERE strategy_option_id IN (
                       SELECT option_value.strategy_option_id FROM strategy_option option_value
                       JOIN strategy_case strategy_case ON strategy_case.strategy_case_id = option_value.strategy_case_id
                       WHERE strategy_case.case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_review_request WHERE strategy_option_id IN (
                       SELECT option_value.strategy_option_id FROM strategy_option option_value
                       JOIN strategy_case strategy_case ON strategy_case.strategy_case_id = option_value.strategy_case_id
                       WHERE strategy_case.case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_inventory_snapshot WHERE strategy_case_id IN (
                       SELECT strategy_case_id FROM strategy_case WHERE case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_price_snapshot WHERE strategy_case_id IN (
                       SELECT strategy_case_id FROM strategy_case WHERE case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_forecast_snapshot WHERE final_selection_id IN (
                       SELECT selection.final_selection_id FROM final_strategy_selection selection
                       JOIN strategy_case strategy_case ON strategy_case.strategy_case_id = selection.strategy_case_id
                       WHERE strategy_case.case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_action WHERE strategy_option_id IN (
                       SELECT option_value.strategy_option_id FROM strategy_option option_value
                       JOIN strategy_case strategy_case ON strategy_case.strategy_case_id = option_value.strategy_case_id
                       WHERE strategy_case.case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_simulation WHERE strategy_option_id IN (
                       SELECT option_value.strategy_option_id FROM strategy_option option_value
                       JOIN strategy_case strategy_case ON strategy_case.strategy_case_id = option_value.strategy_case_id
                       WHERE strategy_case.case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM final_strategy_selection WHERE strategy_case_id IN (
                       SELECT strategy_case_id FROM strategy_case WHERE case_code LIKE 'DEMO-STAT-%')
                """,
                """
                DELETE FROM strategy_option WHERE strategy_case_id IN (
                       SELECT strategy_case_id FROM strategy_case WHERE case_code LIKE 'DEMO-STAT-%')
                """,
                "DELETE FROM strategy_case WHERE case_code LIKE 'DEMO-STAT-%'"
        );
        statements.forEach(statement -> jdbcTemplate.update(statement, NO_PARAMETERS));
    }

    private void validateInsertedLedger() {
        Integer caseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM strategy_case WHERE case_code LIKE 'DEMO-STAT-%' AND is_deleted = 0",
                NO_PARAMETERS, Integer.class);
        Integer mismatchCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM (
                            SELECT result.strategy_execution_result_id
                            FROM strategy_execution_result result
                            JOIN final_strategy_selection selection
                              ON selection.final_selection_id = result.final_selection_id
                            JOIN strategy_case strategy_case
                              ON strategy_case.strategy_case_id = selection.strategy_case_id
                            JOIN strategy_option option_value
                              ON option_value.strategy_option_id = selection.strategy_option_id
                            LEFT JOIN strategy_performance performance
                              ON performance.strategy_option_id = option_value.strategy_option_id
                             AND performance.is_deleted = 0
                            WHERE strategy_case.case_code LIKE 'DEMO-STAT-%'
                              AND result.is_deleted = 0
                            GROUP BY result.strategy_execution_result_id, result.goal_actual_value
                            HAVING COALESCE(SUM(performance.actual_sales_qty), 0) <> result.goal_actual_value
                        )
                        """,
                NO_PARAMETERS, Integer.class);
        if (caseCount == null || caseCount != StrategyStatisticsDemoDataFactory.STRATEGY_COUNT
                || mismatchCount == null || mismatchCount != 0) {
            throw new IllegalStateException("AI 전략 데모 원장 검증에 실패했습니다.");
        }
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record StrategyStatisticsDemoSalesCandidateBuilder(
            long skuId,
            long salesPointId,
            BigDecimal unitCost,
            BigDecimal sellingPrice,
            NavigableMap<LocalDate, StrategyStatisticsDemoSalesDay> dailySales
    ) {
        private StrategyStatisticsDemoSalesCandidateBuilder(
                long skuId, long salesPointId, BigDecimal unitCost, BigDecimal sellingPrice
        ) {
            this(skuId, salesPointId, unitCost, sellingPrice, new TreeMap<>());
        }

        private StrategyStatisticsDemoSalesCandidate build() {
            return new StrategyStatisticsDemoSalesCandidate(
                    skuId, salesPointId, dailySales,
                    unitCost == null ? BigDecimal.ZERO : unitCost,
                    sellingPrice == null ? BigDecimal.ZERO : sellingPrice);
        }
    }

    private static StrategyStatisticsDemoBackfillResponse response(
            LocalDate fromDate,
            LocalDate toDate,
            int requestedCount,
            int createdCount,
            int actionCount
    ) {
        return new StrategyStatisticsDemoBackfillResponse(
                fromDate,
                toDate,
                requestedCount,
                createdCount,
                requestedCount - createdCount,
                actionCount
        );
    }

    private static void validateRange(LocalDate fromDate, LocalDate toDate) {
        long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (days <= 0 || days > MAX_DAYS) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }
}
