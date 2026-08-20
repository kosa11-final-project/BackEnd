package com.stockit.backend.common.exception;

/**
 * 애플리케이션 공통 비즈니스/도메인 예외 클래스입니다.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 에러 코드를 기반으로 기본 메시지를 사용하는 예외를 생성합니다.
     *
     * @param errorCode 에러 코드
     */
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 에러 코드와 커스텀 메시지를 사용하는 예외를 생성합니다.
     *
     * @param errorCode 에러 코드
     * @param message 커스텀 상세 메시지
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message != null && !message.isBlank() ? message : errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 발생한 에러 코드를 반환합니다.
     *
     * @return 에러 코드
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
