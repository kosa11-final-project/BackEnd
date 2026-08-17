package com.stockit.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // ==============================
    // 400 BAD_REQUEST
    // ==============================

    // COMMON
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.", "COMMON-001"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 잘못되었습니다.", "COMMON-002"),

    // ==============================
    // 401 UNAUTHORIZED
    // ==============================

    // AUTH
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다.", "AUTH-001"),

    // ==============================
    // 403 FORBIDDEN
    // ==============================

    // COMMON
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.", "COMMON-003"),

    // ==============================
    // 404 NOT_FOUND
    // ==============================

    // COMMON
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", "COMMON-004"),

    // DEMAND_FORECAST
    DEMAND_FORECAST_NOT_FOUND(HttpStatus.NOT_FOUND, "수요예측 정보를 찾을 수 없습니다.", "DEMAND_FORECAST-001"),
    DEMAND_FORECAST_MODEL_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 수요예측 모델 버전을 찾을 수 없습니다.", "DEMAND_FORECAST-002"),
    DEMAND_FORECAST_SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 SKU가 포함되어 있습니다.", "DEMAND_FORECAST-003"),
    DEMAND_FORECAST_SALES_POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 판매처가 포함되어 있습니다.", "DEMAND_FORECAST-004"),

    // DASHBOARD
    DASHBOARD_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "완료된 대시보드 집계 데이터를 찾을 수 없습니다.", "DASHBOARD-001"),

    // STATISTICS
    STATISTICS_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "완료된 통계 스냅샷을 찾을 수 없습니다.", "STATISTICS-001"),

    // ==============================
    // 405 METHOD_NOT_ALLOWED
    // ==============================

    // COMMON
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.", "COMMON-005"),

    // ==============================
    // 409 CONFLICT
    // ==============================

    // DEMAND_FORECAST
    DEMAND_FORECAST_DUPLICATE_TARGET(HttpStatus.CONFLICT, "요청 내 SKU와 판매처 조합이 중복되었습니다.", "DEMAND_FORECAST-005"),

    // TMP - 구조 확인 후 실제 기능과 함께 제거
    TMP_CONFLICT(HttpStatus.CONFLICT, "테스트 요청이 충돌했습니다.", "TMP-001"),

    // ==============================
    // 500 INTERNAL_SERVER_ERROR
    // ==============================

    // COMMON
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", "COMMON-006");

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;

    ErrorCode(HttpStatus httpStatus, String message, String code) {
        this.httpStatus = httpStatus;
        this.message = message;
        this.code = code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }
}
