package com.stockit.backend.feature.strategy.calculation.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Oracle IN 절의 표현식 1,000개 제한을 넘지 않도록 조회를 분할한다. */
final class OracleInClauseBatcher {

    static final int IN_EXPRESSION_LIMIT = 1_000;

    private OracleInClauseBatcher() {
    }

    static <T> List<T> select(
            List<Long> ids,
            Function<List<Long>, List<T>> query
    ) {
        List<T> result = new ArrayList<>();
        for (int fromIndex = 0;
                fromIndex < ids.size();
                fromIndex += IN_EXPRESSION_LIMIT) {
            int toIndex = Math.min(
                    fromIndex + IN_EXPRESSION_LIMIT,
                    ids.size()
            );
            result.addAll(query.apply(
                    List.copyOf(ids.subList(fromIndex, toIndex))
            ));
        }
        return List.copyOf(result);
    }
}
