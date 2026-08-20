package com.stockit.backend.feature.strategy.service.impl;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionResponse;
import com.stockit.backend.feature.strategy.mapper.StrategyExecutionMapper;
import com.stockit.backend.feature.strategy.service.StrategyExecutionService;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionActionVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionBaseVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionDailySalesVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionInventoryVO;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionPerformanceVO;

@Service
@Transactional(readOnly = true)
public class StrategyExecutionServiceImpl implements StrategyExecutionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final Map<String, String> ACTION_TITLES = Map.of(
            "REALLOCATION", "재고 재할당",
            "RT_TRANSFER", "RT 이동",
            "CHANNEL_EXPANSION", "판매 채널 확대",
            "CHANNEL_CONCENTRATION", "특정 채널 집중 판매"
    );

    private final StrategyExecutionMapper strategyExecutionMapper;

    public StrategyExecutionServiceImpl(StrategyExecutionMapper strategyExecutionMapper) {
        this.strategyExecutionMapper = strategyExecutionMapper;
    }

    @Override
    public List<StrategyExecutionResponse> findAll() {
        List<StrategyExecutionBaseVO> bases = safe(strategyExecutionMapper.selectFinalStrategyExecutions());
        if (bases.isEmpty()) {
            return List.of();
        }
        List<Long> optionIds = bases.stream()
                .map(StrategyExecutionBaseVO::getStrategyOptionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<StrategyExecutionActionVO>> actionsByOption = optionIds.isEmpty()
                ? Map.of()
                : safe(strategyExecutionMapper.selectSupportedActions(optionIds)).stream()
                        .collect(Collectors.groupingBy(
                                StrategyExecutionActionVO::getStrategyOptionId,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        return bases.stream()
                .map(base -> response(
                        base,
                        actionsByOption.getOrDefault(base.getStrategyOptionId(), List.of()),
                        List.of(),
                        List.of(),
                        null
                ))
                .toList();
    }

    @Override
    public StrategyExecutionResponse findByStrategyCaseId(Long strategyCaseId) {
        if (strategyCaseId == null || strategyCaseId <= 0) {
            throw new AppException(ErrorCode.AI_STRATEGY_EXECUTION_NOT_FOUND);
        }
        StrategyExecutionBaseVO base = strategyExecutionMapper.selectFinalStrategyExecution(strategyCaseId);
        if (base == null) {
            throw new AppException(ErrorCode.AI_STRATEGY_EXECUTION_NOT_FOUND);
        }
        List<StrategyExecutionActionVO> actions = safe(strategyExecutionMapper.selectSupportedActions(
                List.of(base.getStrategyOptionId())
        ));
        List<StrategyExecutionInventoryVO> inventories = safe(
                strategyExecutionMapper.selectInventoryResults(strategyCaseId)
        );
        List<StrategyExecutionDailySalesVO> dailySales = safe(
                strategyExecutionMapper.selectDailySales(strategyCaseId)
        );
        StrategyExecutionPerformanceVO performance = strategyExecutionMapper.selectPerformance(
                base.getStrategyOptionId()
        );
        return response(base, actions, inventories, dailySales, performance);
    }

    private static StrategyExecutionResponse response(
            StrategyExecutionBaseVO base,
            List<StrategyExecutionActionVO> actions,
            List<StrategyExecutionInventoryVO> inventories,
            List<StrategyExecutionDailySalesVO> dailySales,
            StrategyExecutionPerformanceVO performance
    ) {
        List<StrategyExecutionResponse.Action> actionResponses = actions.stream()
                .map(action -> action(action, base.getUnitCode()))
                .toList();
        List<StrategyExecutionResponse.DailySales> salesResponses = dailySales.stream()
                .map(sales -> new StrategyExecutionResponse.DailySales(
                        sales.getSalesDate(),
                        sales.getSalesPointId(),
                        sales.getSalesPointCode(),
                        sales.getSalesPointName(),
                        sales.getQuantity(),
                        sales.getRevenue()
                ))
                .toList();
        return new StrategyExecutionResponse(
                base.getStrategyCaseId(),
                base.getCaseCode(),
                executionStatus(base.getCaseStatus()),
                new StrategyExecutionResponse.Product(
                        base.getSkuId(),
                        firstNonBlank(base.getProductName(), base.getSkuName()),
                        base.getSkuCode(),
                        base.getImageUrl()
                ),
                base.getEstablishedAt() == null ? null : base.getEstablishedAt().toLocalDate(),
                null,
                base.getRecommendationReason(),
                null,
                actionResponses,
                inventories.stream().map(StrategyExecutionServiceImpl::inventory).toList(),
                channelResults(dailySales),
                salesResponses,
                salesPointComparison(actions),
                performance(performance),
                base.getLastSyncedAt() == null
                        ? null
                        : base.getLastSyncedAt().atZone(BUSINESS_ZONE).toInstant()
        );
    }

    private static StrategyExecutionResponse.Action action(
            StrategyExecutionActionVO action,
            String unitCode
    ) {
        List<StrategyExecutionResponse.Kpi> kpis = action.getActionQuantity() == null
                ? List.of()
                : List.of(new StrategyExecutionResponse.Kpi(
                        "요청 수량",
                        action.getActionQuantity(),
                        unitCode,
                        true,
                        "미수집"
                ));
        return new StrategyExecutionResponse.Action(
                action.getStrategyActionId(),
                action.getActionType(),
                ACTION_TITLES.getOrDefault(action.getActionType(), action.getActionType()),
                targetLabel(action),
                null,
                List.of(),
                null,
                null,
                null,
                action.getActionQuantity(),
                action.getStartDate(),
                action.getEndDate(),
                salesPoint(action.getSourceSalesPointId(), action.getSourceSalesPointCode(),
                        action.getSourceSalesPointName()),
                salesPoint(action.getTargetSalesPointId(), action.getTargetSalesPointCode(),
                        action.getTargetSalesPointName()),
                warehouse(action.getSourceWarehouseId(), action.getSourceWarehouseCode(),
                        action.getSourceWarehouseName()),
                warehouse(action.getDestinationWarehouseId(), action.getDestinationWarehouseCode(),
                        action.getDestinationWarehouseName()),
                kpis
        );
    }

    private static StrategyExecutionResponse.InventoryResult inventory(
            StrategyExecutionInventoryVO inventory
    ) {
        BigDecimal change = inventory.getCurrentQuantity() == null
                || inventory.getBeforeQuantity() == null
                ? null
                : inventory.getCurrentQuantity().subtract(inventory.getBeforeQuantity());
        String guardrail = inventory.getSafetyStockQuantity() == null
                ? null
                : "안전재고 " + inventory.getSafetyStockQuantity().stripTrailingZeros().toPlainString() + " 유지";
        return new StrategyExecutionResponse.InventoryResult(
                inventory.getLocationName(),
                inventory.getLocationType(),
                inventory.getLocationId(),
                inventory.getLocationCode(),
                inventory.getBeforeQuantity(),
                change,
                inventory.getCurrentQuantity(),
                inventory.getSafetyStockQuantity(),
                guardrail
        );
    }

    private static List<StrategyExecutionResponse.ChannelResult> channelResults(
            List<StrategyExecutionDailySalesVO> dailySales
    ) {
        Map<Long, List<StrategyExecutionDailySalesVO>> grouped = dailySales.stream()
                .collect(Collectors.groupingBy(
                        StrategyExecutionDailySalesVO::getSalesPointId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return grouped.values().stream().map(rows -> {
            StrategyExecutionDailySalesVO first = rows.get(0);
            return new StrategyExecutionResponse.ChannelResult(
                    first.getSalesPointId(),
                    first.getSalesPointName(),
                    null,
                    rows.stream().map(StrategyExecutionDailySalesVO::getQuantity)
                            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                    rows.stream().map(StrategyExecutionDailySalesVO::getRevenue)
                            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                    null
            );
        }).toList();
    }

    private static List<StrategyExecutionResponse.SalesPointComparison> salesPointComparison(
            List<StrategyExecutionActionVO> actions
    ) {
        Map<String, StrategyExecutionResponse.SalesPointComparison> points = new LinkedHashMap<>();
        for (StrategyExecutionActionVO action : actions) {
            addPoint(points, action.getSourceSalesPointId(), action.getSourceSalesPointCode(),
                    action.getSourceSalesPointName(), "SOURCE", "출발 판매처");
            addPoint(points, action.getTargetSalesPointId(), action.getTargetSalesPointCode(),
                    action.getTargetSalesPointName(), "DESTINATION", "대상 판매처");
        }
        return new ArrayList<>(points.values());
    }

    private static void addPoint(
            Map<String, StrategyExecutionResponse.SalesPointComparison> points,
            Long id,
            String code,
            String name,
            String role,
            String label
    ) {
        if (id != null) {
            points.putIfAbsent(role + ":" + id, new StrategyExecutionResponse.SalesPointComparison(
                    id, code, name, role, label
            ));
        }
    }

    private static StrategyExecutionResponse.Performance performance(
            StrategyExecutionPerformanceVO performance
    ) {
        if (performance == null || performance.getPerformanceCount() == null
                || performance.getPerformanceCount() == 0) {
            return null;
        }
        return new StrategyExecutionResponse.Performance(
                performance.getActualSalesQuantity(),
                performance.getActualRevenue(),
                performance.getActualContributionMargin(),
                performance.getActualRemainingQuantity(),
                performance.getMovedQuantity(),
                performance.getDisposedQuantity()
        );
    }

    private static StrategyExecutionResponse.Location salesPoint(Long id, String code, String name) {
        return id == null ? null : new StrategyExecutionResponse.Location(id, code, name, "SALES_POINT");
    }

    private static StrategyExecutionResponse.Location warehouse(Long id, String code, String name) {
        return id == null ? null : new StrategyExecutionResponse.Location(id, code, name, "WAREHOUSE");
    }

    private static String targetLabel(StrategyExecutionActionVO action) {
        String source = firstNonBlank(action.getSourceSalesPointName(), action.getSourceWarehouseName());
        String target = firstNonBlank(action.getTargetSalesPointName(), action.getDestinationWarehouseName());
        if (source == null) {
            return target;
        }
        return target == null ? source : source + " → " + target;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static String executionStatus(String caseStatus) {
        if (caseStatus == null) {
            return null;
        }
        return switch (caseStatus) {
            case "READY_TO_EXECUTE" -> "READY";
            case "EXECUTING" -> "EXECUTING";
            case "EXECUTION_COMPLETED" -> "COMPLETED";
            default -> null;
        };
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
