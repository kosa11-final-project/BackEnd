package com.stockit.backend.feature.strategy.calculation.candidate.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.domain.StrategyType;

/** 같은 정규화 액션에는 재시도해도 같은 후보 ID를 부여한다. */
@Component
public class StrategyCandidateIdGenerator {

    public String generate(
            StrategyType strategyType,
            LocalDate startDate,
            LocalDate endDate,
            List<StrategyCandidate.Action> actions
    ) {
        return generate(List.of(strategyType), startDate, endDate, actions);
    }

    public String generate(
            List<StrategyType> strategyTypes,
            LocalDate startDate,
            LocalDate endDate,
            List<StrategyCandidate.Action> actions
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(strategyTypes).append('|')
                .append(startDate).append('|')
                .append(endDate);
        for (StrategyCandidate.Action action : actions) {
            canonical.append('|').append(action.actionType())
                    .append(':').append(action.source().warehouseId())
                    .append(':').append(action.source().salesPointId())
                    .append('>').append(action.target().warehouseId())
                    .append(':').append(action.target().salesPointId())
                    .append(':').append(plain(action.actionQuantity()))
                    .append(':').append(plain(action.strategyPrice()))
                    .append(':').append(plain(action.discountRate()))
                    .append(':').append(plain(action.estimatedActionCost()));
            if (action.movementCost() != null) {
                StrategyCandidate.MovementCost movement = action.movementCost();
                canonical.append(":MOVE:")
                        .append(movement.transferRouteId()).append(':')
                        .append(movement.transferCostPolicyId()).append(':')
                        .append(plain(movement.weightKg())).append(':')
                        .append(plain(movement.distanceKm())).append(':')
                        .append(plain(movement.costPerKgKm()));
            }
            for (StrategyCandidate.LotAllocation allocation : action.lotAllocations()) {
                canonical.append('[')
                        .append(allocation.inventoryBalanceId()).append(':')
                        .append(allocation.lotId()).append(':')
                        .append(plain(allocation.quantity()))
                        .append(']');
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)
            );
            return "CAND-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
