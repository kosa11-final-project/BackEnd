package com.stockit.backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 표준 에러 코드 정의 열거형입니다.
 */
public enum ErrorCode {

    // ==============================
    // 400 BAD_REQUEST
    // ==============================

    // COMMON
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.", "COMMON-001"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "요청 파라미터가 잘못되었습니다.", "COMMON-002"),

    // AI STRATEGY
    AI_STRATEGY_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AI 전략 생성 요청이 잘못되었습니다.", "AI_STRATEGY-001"),
    AI_STRATEGY_LOT_NOT_BELONG_TO_SKU(HttpStatus.BAD_REQUEST, "요청한 LOT가 대상 SKU에 속하지 않습니다.", "AI_STRATEGY-003"),
    AI_STRATEGY_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "아직 지원하지 않는 AI 전략 유형입니다.", "AI_STRATEGY-004"),
    AI_STRATEGY_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "AI 전략 희망 기간이 허용 범위를 벗어났습니다.", "AI_STRATEGY-005"),
    AI_STRATEGY_START_AFTER_END(HttpStatus.BAD_REQUEST, "AI 전략 희망 시작일은 종료일보다 늦을 수 없습니다.", "AI_STRATEGY-006"),

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

    // AI STRATEGY
    AI_STRATEGY_SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 대상 SKU를 찾을 수 없습니다.", "AI_STRATEGY-007"),
    AI_STRATEGY_LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 대상 LOT를 찾을 수 없거나 사용할 수 없습니다.", "AI_STRATEGY-008"),
    AI_STRATEGY_SALES_POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 대상 판매처를 찾을 수 없습니다.", "AI_STRATEGY-009"),
    AI_STRATEGY_EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 실행 정보를 찾을 수 없습니다.", "AI_STRATEGY-010"),

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

    // AI STRATEGY
    AI_STRATEGY_DUPLICATE_INPUT(HttpStatus.CONFLICT, "AI 전략 생성 요청에 중복된 값이 포함되어 있습니다.", "AI_STRATEGY-002"),

    // TMP - 구조 확인 후 실제 기능과 함께 제거
    TMP_CONFLICT(HttpStatus.CONFLICT, "테스트 요청이 충돌했습니다.", "TMP-001"),

    // ==============================
    // 500 INTERNAL_SERVER_ERROR
    // ==============================

    // COMMON
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", "COMMON-006"),

    // DATABASE
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 처리 중 오류가 발생했습니다.", "DATABASE-001");

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;

    /**
     * 에러 코드 항목을 생성합니다.
     *
     * @param httpStatus 매핑될 HTTP 상태 코드
     * @param message 기본 에러 메시지
     * @param code 비즈니스 에러 코드 문자열
     */
    ErrorCode(HttpStatus httpStatus, String message, String code) {
        this.httpStatus = httpStatus;
        this.message = message;
        this.code = code;
    }

    /**
     * HTTP 상태 코드를 반환합니다.
     *
     * @return HTTP 상태 코드
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 기본 에러 메시지를 반환합니다.
     *
     * @return 기본 에러 메시지
     */
    public String getMessage() {
        return message;
    }

    /**
     * 비즈니스 에러 코드 문자열을 반환합니다.
     *
     * @return 비즈니스 에러 코드
     */
    public String getCode() {
        return code;
    }
}
