package com.stockit.backend.feature.strategy.calculation.candidate.policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.Price;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;

/** 현재 실판매가에서 5% 단위로 할인하되 SKU 최저 판매가를 지킨다. */
@Component
public class DiscountRateCandidatePolicy {

    private static final BigDecimal RATE_STEP = new BigDecimal("0.0500");

    public List<DiscountOption> generate(Price price) {
        if (price == null || price.minimumSellingPrice() == null
                || price.actualPrice().signum() <= 0) {
            return List.of();
        }
        List<DiscountOption> options = new ArrayList<>();
        for (BigDecimal rate = RATE_STEP;
                rate.compareTo(BigDecimal.ONE) < 0;
                rate = rate.add(RATE_STEP)) {
            BigDecimal strategyPrice = CalculationPrecisionPolicy.money(
                    price.actualPrice().multiply(BigDecimal.ONE.subtract(rate))
            );
            if (strategyPrice.compareTo(price.minimumSellingPrice()) < 0) {
                break;
            }
            options.add(new DiscountOption(rate, strategyPrice));
        }
        return List.copyOf(options);
    }

    public record DiscountOption(
            BigDecimal discountRate,
            BigDecimal strategyPrice
    ) {
    }
}
