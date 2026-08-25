package com.stockit.backend.feature.strategy.calculation.policy;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 할인 전략의 수요 탄력성과 판매처 그룹별 할인 상한 설정. */
@ConfigurationProperties(prefix = "app.ai-strategy.simulation.discount")
public class DiscountSimulationProperties {

    private BigDecimal priceElasticity = new BigDecimal("1.0");
    private BigDecimal maximumDemandMultiplier = new BigDecimal("1.5");
    private BigDecimal departmentStoreMaximumDiscountRate = new BigDecimal("0.20");
    private BigDecimal generalMaximumDiscountRate = new BigDecimal("0.30");
    private BigDecimal unknownMaximumDiscountRate = new BigDecimal("0.20");
    private Set<String> departmentStoreCodePrefixes = new LinkedHashSet<>(
            Set.of("DEPT_")
    );
    private Set<String> generalCodePrefixes = new LinkedHashSet<>(
            Set.of("HMART_")
    );
    private Set<String> generalExactCodes = new LinkedHashSet<>(
            Set.of("GREETING", "MODU_MATJIP")
    );

    public BigDecimal getPriceElasticity() { return priceElasticity; }
    public void setPriceElasticity(BigDecimal value) {
        priceElasticity = positive(value, "priceElasticity");
    }

    public BigDecimal getMaximumDemandMultiplier() { return maximumDemandMultiplier; }
    public void setMaximumDemandMultiplier(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException(
                    "maximumDemandMultiplier must be at least one"
            );
        }
        maximumDemandMultiplier = value;
    }

    public BigDecimal getDepartmentStoreMaximumDiscountRate() {
        return departmentStoreMaximumDiscountRate;
    }
    public void setDepartmentStoreMaximumDiscountRate(BigDecimal value) {
        departmentStoreMaximumDiscountRate = discountRate(
                value, "departmentStoreMaximumDiscountRate"
        );
    }

    public BigDecimal getGeneralMaximumDiscountRate() {
        return generalMaximumDiscountRate;
    }
    public void setGeneralMaximumDiscountRate(BigDecimal value) {
        generalMaximumDiscountRate = discountRate(
                value, "generalMaximumDiscountRate"
        );
    }

    public BigDecimal getUnknownMaximumDiscountRate() {
        return unknownMaximumDiscountRate;
    }
    public void setUnknownMaximumDiscountRate(BigDecimal value) {
        unknownMaximumDiscountRate = discountRate(
                value, "unknownMaximumDiscountRate"
        );
    }

    public Set<String> getDepartmentStoreCodePrefixes() {
        return Set.copyOf(departmentStoreCodePrefixes);
    }
    public void setDepartmentStoreCodePrefixes(Set<String> values) {
        departmentStoreCodePrefixes = normalized(values);
    }

    public Set<String> getGeneralCodePrefixes() {
        return Set.copyOf(generalCodePrefixes);
    }
    public void setGeneralCodePrefixes(Set<String> values) {
        generalCodePrefixes = normalized(values);
    }

    public Set<String> getGeneralExactCodes() {
        return Set.copyOf(generalExactCodes);
    }
    public void setGeneralExactCodes(Set<String> values) {
        generalExactCodes = normalized(values);
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static BigDecimal discountRate(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0
                || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " must be between zero and one");
        }
        return value;
    }

    private static Set<String> normalized(Set<String> values) {
        if (values == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) result.add(value.trim());
        }
        return result;
    }
}
