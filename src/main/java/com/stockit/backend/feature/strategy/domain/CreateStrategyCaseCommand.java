package com.stockit.backend.feature.strategy.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 전략 생성 요청을 서비스 계층으로 전달하는 명령
 *
 * <p>판매처와 전략 유형 목록의 입력 순서는 사용자 우선순위이므로 그대로 보존</p>
 */
public record CreateStrategyCaseCommand(
        String caseName,
        Long skuId,
        Long sourceSalesPointId,
        List<Long> lotIds,
        List<Long> candidateSalesPointIds,
        List<StrategyType> strategyTypes,
        LocalDate preferredStartDate,
        LocalDate preferredEndDate
) {

    public CreateStrategyCaseCommand {
        // 호출자가 전달한 목록 변경이 저장 우선순위에 영향을 주지 않도록 방어적 복사
        lotIds = immutableCopy(lotIds);
        candidateSalesPointIds = immutableCopy(candidateSalesPointIds);
        strategyTypes = immutableCopy(strategyTypes);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
