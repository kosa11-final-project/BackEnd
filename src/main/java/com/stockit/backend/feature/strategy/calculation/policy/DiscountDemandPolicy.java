package com.stockit.backend.feature.strategy.calculation.policy;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;
import com.stockit.backend.feature.strategy.calculation.engine.CandidateSimulationException;

/** 정상가 기준 ML 수요에 가격탄력성 기반 할인 수요 배수를 적용한다. */
@Component
public class DiscountDemandPolicy {

    private final DiscountSimulationProperties properties;
    private final SalesPointDiscountPolicy salesPointPolicy;

    public DiscountDemandPolicy(
            DiscountSimulationProperties properties,
            SalesPointDiscountPolicy salesPointPolicy
    ) {
        this.properties = properties;
        this.salesPointPolicy = salesPointPolicy;
    }

    public BigDecimal apply(
            BigDecimal baselineDemand,
            SalesPoint salesPoint,
            BigDecimal discountRate
    ) {
        if (baselineDemand == null || baselineDemand.signum() < 0) {
            throw new CandidateSimulationException(
                    "CALCULATION_FORECAST_INVALID",
                    "Baseline demand must be zero or positive"
            );
        }
        if (!salesPointPolicy.isAllowed(salesPoint, discountRate)) {
            throw new CandidateSimulationException(
                    "CANDIDATE_DISCOUNT_RATE_EXCEEDED",
                    "Discount rate exceeds the sales point policy limit"
            );
        }
        double remainingPriceRatio = BigDecimal.ONE.subtract(discountRate).doubleValue();
        double elasticity = properties.getPriceElasticity().doubleValue();
        BigDecimal multiplier = BigDecimal.valueOf(
                Math.pow(remainingPriceRatio, -elasticity)
        ).min(properties.getMaximumDemandMultiplier());
        return CalculationPrecisionPolicy.quantity(
                baselineDemand.multiply(multiplier)
        );
    }
}
