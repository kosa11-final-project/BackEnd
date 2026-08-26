package com.stockit.backend.feature.demandforecast.alert;

class DemandForecastTeamsAlertDeliveryException extends RuntimeException {
    private final String code;

    DemandForecastTeamsAlertDeliveryException(String code, String message) {
        super(message);
        this.code = code;
    }

    DemandForecastTeamsAlertDeliveryException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    String code() {
        return code;
    }
}
