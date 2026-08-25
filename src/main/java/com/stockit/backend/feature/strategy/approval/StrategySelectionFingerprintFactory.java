package com.stockit.backend.feature.strategy.approval;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.stockit.backend.feature.strategy.result.StrategyGenerationResult;

/** candidateId가 같아도 조정 수량·가격·기간·LOT가 다르면 다른 선택으로 식별한다. */
@Component
public class StrategySelectionFingerprintFactory {

    public String create(
            StrategySelectionInputSource source,
            StrategyGenerationResult.Candidate candidate,
            BigDecimal targetQuantity,
            LocalDate evaluationEndDate
    ) {
        StringBuilder canonical = new StringBuilder()
                .append(source.name()).append('|')
                .append(candidate.candidateId()).append('|')
                .append(decimal(targetQuantity)).append('|')
                .append(candidate.startDate()).append('|')
                .append(candidate.endDate()).append('|')
                .append(evaluationEndDate);
        candidate.actions().forEach(action -> {
            canonical.append("||").append(action.actionType())
                    .append('|').append(action.sourceWarehouseId())
                    .append('|').append(action.sourceSalesPointId())
                    .append('|').append(action.targetWarehouseId())
                    .append('|').append(action.targetSalesPointId())
                    .append('|').append(decimal(action.actionQuantity()))
                    .append('|').append(decimal(action.strategyPrice()))
                    .append('|').append(decimal(action.discountRate()));
            action.lotAllocations().forEach(lot -> canonical
                    .append("|").append(lot.inventoryBalanceId())
                    .append(':').append(lot.lotId())
                    .append(':').append(decimal(lot.quantity()))
                    .append(':').append(lot.priorityNo()));
        });
        return StrategySelectionFingerprintFactoryHash.sha256(canonical.toString());
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

}
