package com.stockit.backend.feature.inventory.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.stockit.backend.common.web.RequestCancellationContext;
import com.stockit.backend.common.web.RequestCancellationToken;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 같은 세션에서 동일한 재고 조회 scope로 들어온 요청을 last-request-wins로 조정합니다.
 * 목록과 요약은 서로 다른 scope를 사용하므로 페이지 진입 시 두 요청이 서로 취소하지 않습니다.
 */
@Component
public class InventoryQueryCancellationCoordinator {

    private final ConcurrentMap<String, RequestCancellationToken> activeRequests = new ConcurrentHashMap<>();

    public <T> T execute(String operation, HttpServletRequest request, Supplier<T> query) {
        RequestCancellationToken token = begin(operation, request);
        RequestCancellationContext.bind(token);
        try {
            T result = query.get();
            token.throwIfCancelled();
            return result;
        } finally {
            RequestCancellationContext.clear();
            complete(operation, request, token);
        }
    }

    private RequestCancellationToken begin(String operation, HttpServletRequest request) {
        String key = scopeKey(operation, request);
        RequestCancellationToken token = new RequestCancellationToken();
        RequestCancellationToken previous = activeRequests.put(key, token);
        if (previous != null) previous.cancel();
        request.setAttribute(RequestCancellationToken.REQUEST_ATTRIBUTE, token);
        request.setAttribute(scopeAttribute(operation), key);
        return token;
    }

    private void complete(String operation, HttpServletRequest request, RequestCancellationToken token) {
        String key = (String) request.getAttribute(scopeAttribute(operation));
        if (key != null) activeRequests.remove(key, token);
    }

    private static String scopeKey(String operation, HttpServletRequest request) {
        var session = request.getSession(false);
        String identity = session == null ? null : session.getId();
        if (identity == null && request.getUserPrincipal() != null) {
            identity = request.getUserPrincipal().getName();
        }
        if (identity == null || identity.isBlank()) identity = "anonymous";
        return identity + "|" + operation;
    }

    private static String scopeAttribute(String operation) {
        return RequestCancellationToken.REQUEST_ATTRIBUTE + ".scope." + operation;
    }
}
