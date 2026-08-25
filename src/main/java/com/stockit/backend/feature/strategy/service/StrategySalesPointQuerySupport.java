package com.stockit.backend.feature.strategy.service;

import java.util.ArrayList;
import java.util.List;

import com.stockit.backend.feature.strategy.mapper.StrategyCaseMapper;

/**
 * Oracle IN 목록 제한을 넘지 않도록 판매처 참조 조회를 분할하는 지원 클래스
 */
public final class StrategySalesPointQuerySupport {

    private static final int ORACLE_IN_EXPRESSION_LIMIT = 1_000;

    private StrategySalesPointQuerySupport() {
    }

    /**
     * 사용자 선택 개수를 제한하지 않고 1,000개 이하의 조회로 나누어 활성 판매처 통합
     */
    public static List<Long> selectActiveSalesPointIds(
            StrategyCaseMapper strategyCaseMapper,
            List<Long> salesPointIds
    ) {
        List<Long> activeIds = new ArrayList<>();
        for (int fromIndex = 0;
                fromIndex < salesPointIds.size();
                fromIndex += ORACLE_IN_EXPRESSION_LIMIT) {
            int toIndex = Math.min(
                    fromIndex + ORACLE_IN_EXPRESSION_LIMIT,
                    salesPointIds.size()
            );
            activeIds.addAll(strategyCaseMapper.selectActiveSalesPointIds(
                    List.copyOf(salesPointIds.subList(fromIndex, toIndex))
            ));
        }
        return List.copyOf(activeIds);
    }
}
