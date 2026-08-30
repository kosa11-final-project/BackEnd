package com.stockit.backend.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.api.FieldErrorDetail;
import com.stockit.backend.feature.inventorysync.demo.InventoryDemoAdjustmentService.DemoRateLimitException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 전역 예외 처리 핸들러입니다.
 * <p>
 * 애플리케이션 전반에서 발생하는 예외를 가로채어 일관된 {@link ApiErrorResponse} 형태로 변환합니다.
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DemoRateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleDemoRateLimit(DemoRateLimitException exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.RATE_LIMITED;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .header("Retry-After", Integer.toString(exception.retryAfterSeconds()))
                .body(ApiErrorResponse.of(errorCode, errorCode.getMessage(), request.getRequestURI()));
    }

    /**
     * 비즈니스 예외({@link AppException})를 처리합니다.
     *
     * @param exception 비즈니스 예외
     * @param request HTTP 요청 객체
     * @return 에러 응답 엔티티
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(
            AppException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        log.warn(
                "AppException: code={}, method={}, path={}",
                errorCode.getCode(),
                request.getMethod(),
                request.getRequestURI()
        );

        ApiErrorResponse response = ApiErrorResponse.of(
                errorCode,
                safeMessage(exception.getMessage(), errorCode.getMessage()),
                exception.getDetails(),
                request.getRequestURI()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    /**
     * `@Valid` 유효성 검증 실패 예외({@link MethodArgumentNotValidException})를 처리합니다.
     *
     * @param exception 유효성 검증 실패 예외
     * @param request HTTP 요청 객체
     * @return 필드 에러 목록이 포함된 에러 응답 엔티티
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> fieldErrors = toFieldErrorDetails(exception.getBindingResult());

        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
        ApiErrorResponse response = ApiErrorResponse.of(
                errorCode,
                errorCode.getMessage(),
                fieldErrors,
                request.getRequestURI()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    /**
     * 바인딩 예외({@link BindException})를 처리합니다.
     *
     * @param exception 바인딩 예외
     * @param request HTTP 요청 객체
     * @return 필드 에러 목록이 포함된 에러 응답 엔티티
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(
            BindException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> fieldErrors = toFieldErrorDetails(exception.getBindingResult());
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiErrorResponse.of(
                errorCode,
                errorCode.getMessage(),
                fieldErrors,
                request.getRequestURI()
        ));
    }

    /**
     * {@link BindingResult}에서 필드별 에러 상세 목록을 추출합니다.
     *
     * @param bindingResult 바인딩 결과
     * @return 필드 에러 목록
     */
    private List<FieldErrorDetail> toFieldErrorDetails(BindingResult bindingResult) {
        return bindingResult.getAllErrors()
                .stream()
                .map(error -> {
                    String field = error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : "_global";
                    String message = error.getDefaultMessage() == null || error.getDefaultMessage().isBlank()
                            ? ErrorCode.INVALID_PARAMETER.getMessage()
                            : error.getDefaultMessage();
                    return new FieldErrorDetail(field, message);
                })
                .toList();
    }

    /**
     * 요청 파라미터 타입 불일치 및 누락 예외를 처리합니다.
     *
     * @param exception 파라미터 관련 예외
     * @param request HTTP 요청 객체
     * @return 에러 응답 엔티티
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleRequestParameterException(
            Exception exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * HTTP 메시지 바디 파싱 실패 예외({@link HttpMessageNotReadableException})를 처리합니다.
     *
     * @param request HTTP 요청 객체
     * @return 에러 응답 엔티티
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.BAD_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 리소스를 찾을 수 없는 예외({@link NoResourceFoundException})를 처리합니다.
     *
     * @param request HTTP 요청 객체
     * @return 에러 응답 엔티티
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFoundException(
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청 예외({@link HttpRequestMethodNotSupportedException})를 처리합니다.
     *
     * @param request HTTP 요청 객체
     * @return 에러 응답 엔티티
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 이미 종료된 비동기 응답에는 오류 Body를 다시 쓰지 않고 연결 종료로 처리합니다.
     *
     * @param exception 비동기 응답을 더 이상 사용할 수 없음을 나타내는 예외
     * @param request HTTP 요청 객체
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(
            AsyncRequestNotUsableException exception,
            HttpServletRequest request
    ) {
        log.debug(
                "Client disconnected from async response. method={}, path={}, reason={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );
    }

    /**
     * 새 필터·정렬 요청이 기존 JDBC 조회를 대체한 경우에는 오류 응답을 만들지 않습니다.
     * 499는 표준 애플리케이션 오류가 아니라 클라이언트가 더 이상 기다리지 않는 상태를
     * 관찰용으로 표현하기 위한 비표준 상태 코드입니다.
     */
    @ExceptionHandler(RequestCancelledException.class)
    public ResponseEntity<Void> handleRequestCancelled(
            RequestCancelledException exception,
            HttpServletRequest request
    ) {
        log.debug(
                "Inventory query cancelled by a newer request. method={}, path={}",
                request.getMethod(),
                request.getRequestURI()
        );
        return ResponseEntity.status(499).build();
    }

    /**
     * 처리되지 않은 모든 일반 예외({@link Exception})를 처리합니다.
     *
     * @param exception 발생 예외
     * @param request HTTP 요청 객체
     * @return 서버 에러 응답 엔티티
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAllException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 데이터베이스 접근 오류({@link DataAccessException})를 처리합니다.
     *
     * @param exception 데이터베이스 예외
     * @param request HTTP 요청 객체
     * @return 데이터베이스 에러 응답 엔티티
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseException(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Database exception: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );
        ErrorCode errorCode = ErrorCode.DATABASE_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode, request.getRequestURI()));
    }

    /**
     * 메시지가 비어있을 경우 대체 메시지를 반환합니다.
     *
     * @param message 원본 메시지
     * @param fallback 대체 메시지
     * @return 안전한 메시지 문자열
     */
    private static String safeMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        // AppException의 사용자용 메시지만 응답으로 사용하고, 예외 체인의 내부 메시지는 노출하지 않습니다.
        return message;
    }
}
