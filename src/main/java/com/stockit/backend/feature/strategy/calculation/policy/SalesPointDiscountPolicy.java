package com.stockit.backend.feature.strategy.calculation.policy;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.SalesPoint;

/** 판매처 코드 정책에 따라 할인 후보 상한을 결정한다. */
@Component
@EnableConfigurationProperties(DiscountSimulationProperties.class)
public class SalesPointDiscountPolicy {

    private final DiscountSimulationProperties properties;

    public SalesPointDiscountPolicy(DiscountSimulationProperties properties) {
        this.properties = properties;
    }

    public DiscountPolicy resolve(SalesPoint salesPoint) {
        if (salesPoint == null) {
            return new DiscountPolicy(
                    SalesPointGroup.UNKNOWN,
                    properties.getUnknownMaximumDiscountRate()
            );
        }
        String code = salesPoint.salesPointCode();
        if (startsWithAny(code, properties.getDepartmentStoreCodePrefixes())) {
            return new DiscountPolicy(
                    SalesPointGroup.DEPARTMENT_STORE,
                    properties.getDepartmentStoreMaximumDiscountRate()
            );
        }
        if (properties.getGeneralExactCodes().contains(code)
                || startsWithAny(code, properties.getGeneralCodePrefixes())) {
            return new DiscountPolicy(
                    SalesPointGroup.GENERAL,
                    properties.getGeneralMaximumDiscountRate()
            );
        }
        return new DiscountPolicy(
                SalesPointGroup.UNKNOWN,
                properties.getUnknownMaximumDiscountRate()
        );
    }

    public boolean isAllowed(SalesPoint salesPoint, BigDecimal discountRate) {
        return discountRate != null && discountRate.signum() > 0
                && discountRate.compareTo(resolve(salesPoint).maximumDiscountRate()) <= 0;
    }

    private static boolean startsWithAny(String code, Iterable<String> prefixes) {
        if (code == null) return false;
        for (String prefix : prefixes) {
            if (code.startsWith(prefix)) return true;
        }
        return false;
    }

    public enum SalesPointGroup {
        DEPARTMENT_STORE,
        GENERAL,
        UNKNOWN
    }

    public record DiscountPolicy(
            SalesPointGroup group,
            BigDecimal maximumDiscountRate
    ) {
    }
}
