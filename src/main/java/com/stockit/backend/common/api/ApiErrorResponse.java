package com.stockit.backend.common.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.common.logging.RequestLoggingFilter;

/**
 * API 공통 에러 응답 레코드입니다.
 *
 * @param code 에러 코드
 * @param message 에러 메시지
 * @param details 비즈니스 오류 해결에 필요한 구조화된 상세 정보
 * @param fieldErrors 필드 검증 에러 목록
 * @param path 요청 경로
 * @param requestId 요청 추적 식별자 (MDC)
 * @param timestamp 발생 일시
 */
@JsonPropertyOrder({"code", "message", "details", "fieldErrors", "path", "requestId", "timestamp"})
public record ApiErrorResponse(
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Object details,
        List<FieldErrorDetail> fieldErrors,
        String path,
        String requestId,
        Instant timestamp
) {

    /**
     * 유효성을 검증하고 필드 에러 목록을 불변 리스트로 설정하는 컴팩트 생성자입니다.
     */
    public ApiErrorResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /**
     * 에러 코드와 경로로 기본 메시지를 가진 에러 응답을 생성합니다.
     *
     * @param errorCode 에러 코드
     * @param path 요청 경로
     * @return 에러 응답 객체
     */
    public static ApiErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, errorCode.getMessage(), null, List.of(), path);
    }

    /**
     * 에러 코드, 커스텀 메시지, 경로로 에러 응답을 생성합니다.
     *
     * @param errorCode 에러 코드
     * @param message 커스텀 에러 메시지
     * @param path 요청 경로
     * @return 에러 응답 객체
     */
    public static ApiErrorResponse of(ErrorCode errorCode, String message, String path) {
        return of(errorCode, message, null, List.of(), path);
    }

    /** 에러 코드, 커스텀 메시지와 구조화된 상세 정보를 가진 응답을 생성합니다. */
    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            Object details,
            String path
    ) {
        return of(errorCode, message, details, List.of(), path);
    }

    /**
     * 에러 코드, 커스텀 메시지, 필드 검증 에러 목록, 경로로 에러 응답을 생성합니다.
     *
     * @param errorCode 에러 코드
     * @param message 커스텀 에러 메시지
     * @param fieldErrors 필드 검증 에러 목록
     * @param path 요청 경로
     * @return 에러 응답 객체
     */
    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            List<FieldErrorDetail> fieldErrors,
            String path
    ) {
        return of(errorCode, message, null, fieldErrors, path);
    }

    private static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            Object details,
            List<FieldErrorDetail> fieldErrors,
            String path
    ) {
        return new ApiErrorResponse(
                errorCode.getCode(),
                message,
                details,
                fieldErrors,
                path,
                MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY),
                Instant.now()
        );
    }
}
