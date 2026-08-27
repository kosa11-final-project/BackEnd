package com.stockit.backend.feature.inventorysync.risk;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.inventory.risk.RiskAssessmentInput;

/** U5 risk writer용 single-query snapshot loader입니다. */
@Component
public class InventorySyncRiskScopeSnapshotLoader implements InventorySyncRiskWriter.RiskScopeSnapshotLoader {
    private final InventorySyncRiskSnapshotMapper mapper;
    private final Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));

    public InventorySyncRiskScopeSnapshotLoader(InventorySyncRiskSnapshotMapper mapper) { this.mapper = mapper; }

    public Set<String> findScopesRequiringRuleVersion(String ruleVersion) {
        if (ruleVersion == null || ruleVersion.isBlank()) {
            throw new IllegalArgumentException("risk rule version is required");
        }
        return new LinkedHashSet<>(mapper.selectScopesRequiringRuleVersion(ruleVersion));
    }

    public Set<String> findScopesRequiringDailyRefresh(LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new IllegalArgumentException("daily risk refresh date is required");
        }
        return new LinkedHashSet<>(mapper.selectScopesRequiringDailyRefresh(asOfDate));
    }

    @Override
    public List<InventorySyncRiskWriter.RiskScopeSnapshot> load(Set<String> affectedScopes) {
        return load(affectedScopes, LocalDate.now(clock));
    }

    @Override
    public List<InventorySyncRiskWriter.RiskScopeSnapshot> load(
            Set<String> affectedScopes,
            LocalDate asOfDate
    ) {
        if (affectedScopes == null || affectedScopes.isEmpty()) return List.of();
        if (asOfDate == null) {
            throw new IllegalArgumentException("risk snapshot date is required");
        }
        LocalDate baseDate = asOfDate;
        Map<String, List<InventorySyncRiskSnapshotMapper.RiskScopeRow>> grouped = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(affectedScopes);
        for (int start = 0; start < keys.size(); start += 500) {
            Set<String> chunk = Set.copyOf(keys.subList(start, Math.min(start + 500, keys.size())));
            for (var row : mapper.selectAffectedScopeSnapshot(chunk, baseDate)) {
                // InventorySyncAttemptBuffer가 기록하는 키와 동일하게 ID 기반으로 그룹화합니다.
                // 화면 표시용 SKU/판매처 코드는 RiskAssessmentInput에만 사용합니다.
                String scopeKey = Objects.toString(row.getSkuId(), row.getSkuCode()) + ":"
                        + Objects.toString(row.getSalesPointId(), "UNASSIGNED");
                grouped.computeIfAbsent(scopeKey, ignored -> new ArrayList<>()).add(row);
            }
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
            List<InventorySyncRiskSnapshotMapper.RiskScopeRow> rows = entry.getValue().stream()
                    .sorted(Comparator.comparing(InventorySyncRiskSnapshotMapper.RiskScopeRow::getInventoryBalanceId))
                    .toList();
            var first = rows.get(0);
            List<RiskAssessmentInput.LotRiskItem> lots = rows.stream()
                    .filter(row -> row.getLotId() != null)
                    .map(row -> new RiskAssessmentInput.LotRiskItem(row.getLotId(), row.getLotNumber(), row.getExpiryDate(), row.getSaleStopDate(), row.getReceivedDate(), row.getLotQty(), row.getLotStatus()))
                    .toList();
            BigDecimal totalOnHand = rows.stream()
                    .map(InventorySyncRiskSnapshotMapper.RiskScopeRow::getOnHandQty)
                    .filter(value -> value != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            LocalDate forecastDate = first.getForecastBaseDate();
            boolean forecastAvailable = first.getPredictedQtyD7() != null
                    && first.getPredictedQtyD14() != null
                    && first.getPredictedQtyD30() != null;
            boolean stale = forecastDate != null && forecastDate.isBefore(baseDate.minusDays(14));
            var input = new RiskAssessmentInput(
                    first.getSkuCode(), first.getSalesPointCode(), totalOnHand,
                    first.getPredictedQtyD7(), first.getPredictedQtyD14(), first.getPredictedQtyD30(),
                    first.getSafetyStockQty(), forecastDate == null ? baseDate : forecastDate,
                    lots, forecastAvailable, stale, baseDate
            );
            List<Long> siblingIds = rows.stream().skip(1).map(InventorySyncRiskSnapshotMapper.RiskScopeRow::getInventoryBalanceId).toList();
            return new InventorySyncRiskWriter.RiskScopeSnapshot(first.getInventoryBalanceId(), first.getForecastId(), input, siblingIds);
        }).toList();
    }
}
