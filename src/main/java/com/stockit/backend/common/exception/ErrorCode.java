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

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", "COMMON-007"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "요청 본문이 허용된 크기를 초과했습니다.", "COMMON-008"),

    // AI STRATEGY
    AI_STRATEGY_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "AI 전략 생성 요청이 잘못되었습니다.", "AI_STRATEGY-001"),
    AI_STRATEGY_LOT_NOT_BELONG_TO_SKU(HttpStatus.BAD_REQUEST, "요청한 LOT가 대상 SKU에 속하지 않습니다.", "AI_STRATEGY-003"),
    AI_STRATEGY_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "아직 지원하지 않는 AI 전략 유형입니다.", "AI_STRATEGY-004"),
    AI_STRATEGY_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "AI 전략 희망 기간이 허용 범위를 벗어났습니다.", "AI_STRATEGY-005"),
    AI_STRATEGY_START_AFTER_END(HttpStatus.BAD_REQUEST, "AI 전략 희망 시작일은 종료일보다 늦을 수 없습니다.", "AI_STRATEGY-006"),
    AI_STRATEGY_SIMULATION_INVALID(HttpStatus.BAD_REQUEST, "AI 전략 조정 조건이 실행 가능 범위를 벗어났습니다.", "AI_STRATEGY-012"),
    AI_STRATEGY_INVALID_REVIEWERS(HttpStatus.BAD_REQUEST, "AI 전략 검토자 선택이 올바르지 않습니다.", "AI_STRATEGY-015"),
    AI_STRATEGY_PERIOD_STALE(HttpStatus.BAD_REQUEST, "AI 전략 시작일이 현재 실행 가능한 날짜보다 과거입니다.", "AI_STRATEGY-018"),
    AI_STRATEGY_SELLABLE_END_EXCEEDED(HttpStatus.BAD_REQUEST, "AI 전략 종료일이 재고의 판매 가능 기간을 초과했습니다.", "AI_STRATEGY-019"),

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
    DEMAND_FORECAST_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "수요예측 실행 정보를 찾을 수 없습니다.", "DEMAND_FORECAST-006"),

    // DASHBOARD
    DASHBOARD_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "완료된 대시보드 집계 데이터를 찾을 수 없습니다.", "DASHBOARD-001"),

    // STATISTICS
    STATISTICS_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "완료된 통계 스냅샷을 찾을 수 없습니다.", "STATISTICS-001"),

    // AI STRATEGY
    AI_STRATEGY_SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 대상 SKU를 찾을 수 없습니다.", "AI_STRATEGY-007"),
    AI_STRATEGY_LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 대상 LOT를 찾을 수 없거나 사용할 수 없습니다.", "AI_STRATEGY-008"),
    AI_STRATEGY_SALES_POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 대상 판매처를 찾을 수 없습니다.", "AI_STRATEGY-009"),
    AI_STRATEGY_EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 실행 정보를 찾을 수 없습니다.", "AI_STRATEGY-010"),
    AI_STRATEGY_CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 생성 요청을 찾을 수 없습니다.", "AI_STRATEGY-011"),
    AI_STRATEGY_CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 전략 후보를 찾을 수 없습니다.", "AI_STRATEGY-013"),
    AI_STRATEGY_REVIEWER_NOT_FOUND(HttpStatus.NOT_FOUND, "선택한 AI 전략 검토자를 찾을 수 없습니다.", "AI_STRATEGY-016"),

    // ==============================
    // 410 GONE
    // ==============================

    AI_STRATEGY_RESULT_EXPIRED(HttpStatus.GONE, "AI 전략 계산 결과의 보관 기간이 만료되었습니다.", "AI_STRATEGY-014"),

    // ==============================
    // 405 METHOD_NOT_ALLOWED
    // ==============================

    // COMMON
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다.", "COMMON-005"),

    // ==============================
    // 409 CONFLICT
    // ==============================

    // INVENTORY_SYNC
    INVENTORY_SYNC_CONFLICT(HttpStatus.CONFLICT, "동기화 시연 요청이 현재 상태와 충돌했습니다.", "INVENTORY_SYNC-001"),

    // DEMAND_FORECAST
    DEMAND_FORECAST_DUPLICATE_TARGET(HttpStatus.CONFLICT, "요청 내 SKU와 판매처 조합이 중복되었습니다.", "DEMAND_FORECAST-005"),
    DEMAND_FORECAST_BATCH_CONFLICT(HttpStatus.CONFLICT, "이미 수신한 배치의 내용과 요청이 일치하지 않습니다.", "DEMAND_FORECAST-007"),
    DEMAND_FORECAST_RUN_CONFLICT(HttpStatus.CONFLICT, "수요예측 실행 상태 또는 배치 정보가 요청과 일치하지 않습니다.", "DEMAND_FORECAST-008"),

    // AI STRATEGY
    AI_STRATEGY_DUPLICATE_INPUT(HttpStatus.CONFLICT, "AI 전략 생성 요청에 중복된 값이 포함되어 있습니다.", "AI_STRATEGY-002"),
    AI_STRATEGY_SELECTION_CONFLICT(HttpStatus.CONFLICT, "이미 확정된 AI 전략과 요청이 충돌합니다.", "AI_STRATEGY-017"),
    AI_STRATEGY_CASE_NOT_READY(HttpStatus.CONFLICT, "AI 전략 생성 결과가 아직 조정 가능한 상태가 아닙니다.", "AI_STRATEGY-020"),
    AI_STRATEGY_PERFORMANCE_SYNC_CONFLICT(HttpStatus.CONFLICT, "전략 성과 동기화가 이미 진행 중입니다.", "AI_STRATEGY-021"),
    AI_STRATEGY_RETRY_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상태에서는 AI 전략 생성을 재시도할 수 없습니다.", "AI_STRATEGY-022"),
    AI_STRATEGY_RETRY_DATE_ADJUSTMENT_REQUIRED(HttpStatus.CONFLICT, "기존 전략의 판매 시작일이 지났습니다.", "AI_STRATEGY-023"),
    AI_STRATEGY_RETRY_PERIOD_EXPIRED(HttpStatus.CONFLICT, "기존 전략의 판매 기간이 모두 지났습니다. 조건을 수정하여 새 전략을 생성해 주세요.", "AI_STRATEGY-024"),
    AI_STRATEGY_RETRY_CONDITIONS_STALE(HttpStatus.CONFLICT, "기존 전략의 판매 조건이 현재 실행 가능 범위를 벗어났습니다.", "AI_STRATEGY-025"),
    AI_STRATEGY_RETRY_REFERENCE_CHANGED(HttpStatus.CONFLICT, "기존 요청의 재고 또는 판매처 상태가 변경되었습니다.", "AI_STRATEGY-026"),
    AI_STRATEGY_RETRY_PAYLOAD_INVALID(HttpStatus.CONFLICT, "기존 AI 전략 요청 정보를 복원할 수 없습니다.", "AI_STRATEGY-027"),

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
