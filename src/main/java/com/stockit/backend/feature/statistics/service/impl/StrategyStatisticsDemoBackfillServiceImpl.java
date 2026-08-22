package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.statistics.dto.response.StrategyStatisticsDemoBackfillResponse;
import com.stockit.backend.feature.statistics.service.StrategyStatisticsDemoBackfillService;

@Service
public class StrategyStatisticsDemoBackfillServiceImpl implements StrategyStatisticsDemoBackfillService {
    private static final int MAX_DAYS = 366;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Map<String, Object> NO_PARAMETERS = Map.of();

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
                strategy_action_id, strategy_option_id, target_sales_point_id,
                source_warehouse_id, destination_warehouse_id, action_type,
                action_quantity, strategy_price, discount_rate, start_date, end_date,
                estimated_action_cost, action_order,
                created_at, updated_at, created_by, updated_by, is_deleted
            ) VALUES (
                :actionId, :optionId, :targetSalesPointId,
                :sourceWarehouseId, :destinationWarehouseId, :actionType,
                :actionQuantity, :strategyPrice, :discountRate, :startDate, :endDate,
                :estimatedActionCost, :actionOrder,
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

    public StrategyStatisticsDemoBackfillServiceImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataFactory = new StrategyStatisticsDemoDataFactory();
    }

    @Override
    @Transactional
    public StrategyStatisticsDemoBackfillResponse backfill(LocalDate fromDate, LocalDate toDate) {
        LocalDate resolvedTo = toDate == null ? LocalDate.now(BUSINESS_ZONE) : toDate;
        LocalDate resolvedFrom = fromDate == null ? resolvedTo.minusMonths(6) : fromDate;
        validateRange(resolvedFrom, resolvedTo);
        ensureExecutionResultTableExists();

        StrategyStatisticsDemoDimensions dimensions = loadDimensions();
        List<StrategyStatisticsDemoData> requested = dataFactory.create(
                resolvedFrom,
                resolvedTo,
                dimensions
        );
        Set<Long> existingCaseIds = new HashSet<>(jdbcTemplate.queryForList(
                """
                        SELECT strategy_case_id
                        FROM strategy_case
                        WHERE strategy_case_id >= :fromId
                          AND strategy_case_id < :toId
                        """,
                Map.of(
                        "fromId", StrategyStatisticsDemoDataFactory.CASE_ID_BASE,
                        "toId", StrategyStatisticsDemoDataFactory.OPTION_ID_BASE
                ),
                Long.class
        ));
        List<StrategyStatisticsDemoData> created = requested.stream()
                .filter(value -> !existingCaseIds.contains(value.caseId()))
                .toList();

        if (!created.isEmpty()) {
            insertCases(created);
            insertOptions(created);
            insertSimulations(created);
            int actionCount = insertActions(created);
            insertSelections(created);
            insertResults(created);
            return response(resolvedFrom, resolvedTo, requested.size(), created.size(), actionCount);
        }
        return response(resolvedFrom, resolvedTo, requested.size(), 0, 0);
    }

    private StrategyStatisticsDemoDimensions loadDimensions() {
        List<Long> skuIds = jdbcTemplate.queryForList(
                """
                        SELECT sku_id FROM sku
                        WHERE active_yn = 'Y' AND is_deleted = 0
                        ORDER BY sku_id FETCH FIRST 500 ROWS ONLY
                        """,
                NO_PARAMETERS,
                Long.class
        );
        List<Long> offlinePointIds = selectSalesPointIds("OFFLINE");
        List<Long> onlinePointIds = selectSalesPointIds("ONLINE");
        List<Long> warehouseIds = jdbcTemplate.queryForList(
                """
                        SELECT warehouse_id FROM warehouse
                        WHERE active_yn = 'Y' AND is_deleted = 0
                        ORDER BY warehouse_id
                        """,
                NO_PARAMETERS,
                Long.class
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
        if (skuIds.isEmpty() || offlinePointIds.isEmpty() || onlinePointIds.isEmpty()
                || warehouseIds.isEmpty() || ownerIds.isEmpty() || syncRunIds.isEmpty()) {
            throw new IllegalStateException(
                    "AI 전략 데모 이력 생성에 필요한 SKU, 온·오프라인 판매처, 물류센터, 사용자 또는 성공 동기화가 없습니다."
            );
        }
        return new StrategyStatisticsDemoDimensions(
                skuIds,
                offlinePointIds,
                onlinePointIds,
                warehouseIds,
                ownerIds.get(0),
                syncRunIds.get(0)
        );
    }

    private List<Long> selectSalesPointIds(String channelType) {
        return jdbcTemplate.queryForList(
                """
                        SELECT point.sales_point_id
                        FROM sales_point point
                        JOIN sales_channel channel
                          ON channel.sales_channel_id = point.sales_channel_id
                         AND channel.active_yn = 'Y'
                         AND channel.is_deleted = 0
                        WHERE point.active_yn = 'Y'
                          AND point.is_deleted = 0
                          AND channel.channel_type = :channelType
                        ORDER BY point.sales_point_id
                        """,
                Map.of("channelType", channelType),
                Long.class
        );
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
                .addValue("requestPayloadJson", "{\"demo\":true,\"source\":\"statistics-demo-backfill\"}")
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
