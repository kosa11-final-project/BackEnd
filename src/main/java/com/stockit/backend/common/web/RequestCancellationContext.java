package com.stockit.backend.common.web;

/** 현재 실행 중인 조회 요청의 취소 토큰을 보관하는 thread-local context입니다. */
public final class RequestCancellationContext {

    private static final ThreadLocal<RequestCancellationToken> CURRENT = new ThreadLocal<>();

    private RequestCancellationContext() {
    }

    public static RequestCancellationToken current() {
        return CURRENT.get();
    }

    public static void bind(RequestCancellationToken token) {
        if (token == null) CURRENT.remove();
        else CURRENT.set(token);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
