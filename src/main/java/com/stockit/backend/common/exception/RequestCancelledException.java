package com.stockit.backend.common.exception;

/** 새 조회 요청으로 기존 조회가 대체되었음을 나타내는 내부 제어 예외입니다. */
public class RequestCancelledException extends RuntimeException {

    public RequestCancelledException() {
        super("요청이 취소되었습니다.");
    }

    public RequestCancelledException(Throwable cause) {
        super("요청이 취소되었습니다.", cause);
    }
}
