package com.stockit.backend.feature.strategy.alert;

/** Teams 운영 알림 전달 자체가 실패했음을 호출자에게 구분해 전달합니다. */
class AiStrategyTeamsAlertDeliveryException extends RuntimeException {
    private final String code;

    AiStrategyTeamsAlertDeliveryException(String code, String message) {
        super(message);
        this.code = code;
    }

    AiStrategyTeamsAlertDeliveryException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    String code() {
        return code;
    }
}
