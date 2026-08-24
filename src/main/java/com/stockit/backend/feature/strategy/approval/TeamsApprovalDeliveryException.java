package com.stockit.backend.feature.strategy.approval;

/** Power Automate 호출 실패를 Reviewer별 FAILED 상태로 변환하기 위한 예외. */
public class TeamsApprovalDeliveryException extends RuntimeException {

    private final String code;

    public TeamsApprovalDeliveryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TeamsApprovalDeliveryException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
