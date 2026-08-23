package com.stockit.backend.feature.strategy.calculation.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 전략 계산 전체에서 수량, 금액, 비율의 정밀도를 일관되게 적용한다. */
public final class CalculationPrecisionPolicy {

    public static final int QUANTITY_SCALE = 3;
    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 4;

    private CalculationPrecisionPolicy() {
    }

    public static BigDecimal quantity(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.DOWN);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal rate(BigDecimal value) {
        return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }
}
