package com.stockit.backend.feature.strategy.approval;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.service.StrategyDateTimeProvider;

/** 최종 저장 직전 현재 DB 입력과 생성 Snapshot의 차이를 구조화해 검증한다. */
@Component
public class StrategySelectionExecutabilityValidator {

    private final StrategySelectionConditionChangeDetector changeDetector;
    private final StrategyTransferInputFreshnessValidator transferFreshnessValidator;
    private final StrategyDateTimeProvider dateTimeProvider;

    public StrategySelectionExecutabilityValidator(
            StrategySelectionConditionChangeDetector changeDetector,
            StrategyTransferInputFreshnessValidator transferFreshnessValidator,
            StrategyDateTimeProvider dateTimeProvider
    ) {
        this.changeDetector = changeDetector;
        this.transferFreshnessValidator = transferFreshnessValidator;
        this.dateTimeProvider = dateTimeProvider;
    }

    public void validate(ResolvedStrategySelection resolved, LocalDate businessDate) {
        List<StrategyExecutionConditionChange> changes = changeDetector.detect(
                resolved, businessDate
        );
        if (!changes.isEmpty()) {
            changed(resolved, changes);
        }

        try {
            transferFreshnessValidator.validate(resolved);
        } catch (AppException exception) {
            if (exception.getErrorCode()
                    != ErrorCode.AI_STRATEGY_SELECTION_CONFLICT) {
                throw exception;
            }
            changed(resolved, List.of(new StrategyExecutionConditionChange(
                    StrategyExecutionConditionChangeType.TRANSFER_INPUT_CHANGED,
                    "transferInput",
                    "재고 이동 계산 조건",
                    null,
                    "GENERATED_SNAPSHOT",
                    "CHANGED",
                    "UNCHANGED",
                    null,
                    null,
                    exception.getMessage()
            )));
        }
    }

    private void changed(
            ResolvedStrategySelection resolved,
            List<StrategyExecutionConditionChange> changes
    ) {
        throw new StrategyExecutionConditionChangedException(
                new StrategyExecutionConditionChangedDetails(
                        resolved.strategyCaseId(),
                        resolved.option().candidate().candidateId(),
                        dateTimeProvider.now(),
                        changes
                )
        );
    }
}
