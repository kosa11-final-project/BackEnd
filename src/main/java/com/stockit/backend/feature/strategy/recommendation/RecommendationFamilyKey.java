package com.stockit.backend.feature.strategy.recommendation;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.domain.StrategyType;

/** 수량·할인율·기간을 제외한, 사용자에게 구분해서 보여줄 전략 실행 구조. */
record RecommendationFamilyKey(
        List<StrategyType> strategyTypes,
        List<ActionKey> actions
) {

    private static final Comparator<Long> NULLABLE_LONG_ORDER =
            Comparator.nullsFirst(Comparator.naturalOrder());

    private static final Comparator<ActionKey> ACTION_ORDER = Comparator
            .comparing(ActionKey::actionType, Comparator.comparing(StrategyType::name))
            .thenComparing(ActionKey::sourceWarehouseId, NULLABLE_LONG_ORDER)
            .thenComparing(ActionKey::sourceSalesPointId, NULLABLE_LONG_ORDER)
            .thenComparing(ActionKey::targetWarehouseId, NULLABLE_LONG_ORDER)
            .thenComparing(ActionKey::targetSalesPointId, NULLABLE_LONG_ORDER);

    RecommendationFamilyKey {
        strategyTypes = List.copyOf(strategyTypes);
        actions = List.copyOf(actions);
    }

    static RecommendationFamilyKey from(StrategyCandidate candidate) {
        return new RecommendationFamilyKey(
                candidate.strategyTypes().stream()
                        .sorted(Comparator.comparing(StrategyType::name))
                        .toList(),
                candidate.actions().stream()
                        .map(action -> new ActionKey(
                                action.actionType(),
                                action.source().warehouseId(),
                                action.source().salesPointId(),
                                action.target().warehouseId(),
                                action.target().salesPointId()
                        ))
                        .sorted(ACTION_ORDER)
                        .toList()
        );
    }

    /** LLM이 같은 실행 구조를 중복 선택하지 않도록 전달하는 안정적인 식별자. */
    String externalId() {
        String types = strategyTypes.stream()
                .map(Enum::name)
                .collect(Collectors.joining("+"));
        String actionValues = actions.stream()
                .map(ActionKey::externalId)
                .collect(Collectors.joining(";"));
        return types + "|" + actionValues;
    }

    record ActionKey(
            StrategyType actionType,
            Long sourceWarehouseId,
            Long sourceSalesPointId,
            Long targetWarehouseId,
            Long targetSalesPointId
    ) {
        private String externalId() {
            return actionType.name()
                    + ":W" + value(sourceWarehouseId)
                    + ":S" + value(sourceSalesPointId)
                    + ">W" + value(targetWarehouseId)
                    + ":S" + value(targetSalesPointId);
        }

        private static String value(Long value) {
            return value == null ? "_" : value.toString();
        }
    }
}
