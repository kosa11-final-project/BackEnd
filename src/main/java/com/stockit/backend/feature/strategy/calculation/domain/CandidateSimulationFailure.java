package com.stockit.backend.feature.strategy.calculation.domain;

/** 후보 하나만 실행할 수 없을 때 전체 Case를 중단하지 않고 남기는 사유. */
public record CandidateSimulationFailure(
        String candidateId,
        String code,
        String message
) {
    public CandidateSimulationFailure {
        if (candidateId == null || candidateId.isBlank()
                || code == null || code.isBlank()
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("candidate simulation failure is invalid");
        }
    }
}
