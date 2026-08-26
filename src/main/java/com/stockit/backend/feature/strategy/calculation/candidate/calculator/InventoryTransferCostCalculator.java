package com.stockit.backend.feature.strategy.calculation.candidate.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.calculation.candidate.domain.CandidateExclusionReason;
import com.stockit.backend.feature.strategy.calculation.candidate.domain.StrategyCandidate;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.PhysicalLocation;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.TransferCostPolicy;
import com.stockit.backend.feature.strategy.calculation.domain.StrategyCalculationContext.TransferRoute;
import com.stockit.backend.feature.strategy.calculation.engine.CalculationPrecisionPolicy;

/** SKU 순중량 × 이동수량 × 단방향 도로거리 × kg·km 요율 계산기. */
@Component
public class InventoryTransferCostCalculator {

    private static final int WEIGHT_SCALE = 6;

    public StrategyCandidate.MovementCost calculate(
            StrategyCalculationContext context,
            StrategyCandidate.Location source,
            StrategyCandidate.Location target,
            BigDecimal quantity,
            LocalDate strategyStartDate
    ) {
        PhysicalLocation physicalSource = PhysicalLocation.of(
                source.warehouseId(), source.salesPointId()
        );
        PhysicalLocation physicalTarget = PhysicalLocation.of(
                target.warehouseId(), target.salesPointId()
        );
        TransferRoute route = resolveRoute(
                context.transferRoutes(), physicalSource, physicalTarget
        );
        TransferCostPolicy policy = resolvePolicy(
                context.transferCostPolicies(), strategyStartDate
        );
        BigDecimal unitWeightKg = unitWeightKg(context);
        BigDecimal totalWeightKg = unitWeightKg.multiply(quantity)
                .setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
        BigDecimal estimatedCost = CalculationPrecisionPolicy.money(
                totalWeightKg
                        .multiply(route.distanceKm())
                        .multiply(policy.costPerKgKm())
        );
        return new StrategyCandidate.MovementCost(
                route.transferRouteId(),
                policy.transferCostPolicyId(),
                totalWeightKg,
                route.distanceKm(),
                policy.costPerKgKm(),
                estimatedCost
        );
    }

    private static TransferRoute resolveRoute(
            List<TransferRoute> routes,
            PhysicalLocation source,
            PhysicalLocation target
    ) {
        List<TransferRoute> matches = routes.stream()
                .filter(route -> route.source().equals(source))
                .filter(route -> route.destination().equals(target))
                .toList();
        if (matches.isEmpty()) {
            throw failure(
                    CandidateExclusionReason.TRANSFER_ROUTE_NOT_FOUND,
                    "Active transfer route does not exist: " + source + " -> " + target
            );
        }
        if (matches.size() > 1) {
            throw failure(
                    CandidateExclusionReason.TRANSFER_ROUTE_AMBIGUOUS,
                    "Multiple active transfer routes exist: " + source + " -> " + target
            );
        }
        return matches.get(0);
    }

    private static TransferCostPolicy resolvePolicy(
            List<TransferCostPolicy> policies,
            LocalDate strategyStartDate
    ) {
        List<TransferCostPolicy> matches = policies.stream()
                .filter(policy -> policy.appliesOn(strategyStartDate))
                .toList();
        if (matches.isEmpty()) {
            throw failure(
                    CandidateExclusionReason.TRANSFER_COST_POLICY_NOT_FOUND,
                    "Transfer cost policy does not exist for " + strategyStartDate
            );
        }
        if (matches.size() > 1) {
            throw failure(
                    CandidateExclusionReason.TRANSFER_COST_POLICY_AMBIGUOUS,
                    "Multiple transfer cost policies exist for " + strategyStartDate
            );
        }
        return matches.get(0);
    }

    private static BigDecimal unitWeightKg(StrategyCalculationContext context) {
        BigDecimal netWeight = context.sku().netWeight();
        String weightUnit = context.sku().weightUnit();
        if (netWeight == null || weightUnit == null) {
            throw failure(
                    CandidateExclusionReason.SKU_WEIGHT_NOT_FOUND,
                    "SKU net weight and weight unit are required for RT_TRANSFER"
            );
        }
        return switch (weightUnit.toUpperCase(Locale.ROOT)) {
            case "KG" -> netWeight.setScale(WEIGHT_SCALE, RoundingMode.HALF_UP);
            case "G" -> netWeight.divide(
                    new BigDecimal("1000"), WEIGHT_SCALE, RoundingMode.HALF_UP
            );
            default -> throw failure(
                    CandidateExclusionReason.SKU_WEIGHT_UNIT_UNSUPPORTED,
                    "Unsupported SKU weight unit: " + weightUnit
            );
        };
    }

    private static InventoryTransferCostCalculationException failure(
            CandidateExclusionReason reason,
            String message
    ) {
        return new InventoryTransferCostCalculationException(reason, message);
    }
}
