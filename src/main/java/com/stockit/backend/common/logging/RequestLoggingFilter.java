package com.stockit.backend.common.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API 요청을 requestId와 처리 시간 중심으로 기록합니다.
 *
 * <p>요청 본문·쿼리 파라미터·쿠키는 기록하지 않아 로그인 정보와 CSRF 토큰이
 * 로그에 남지 않도록 합니다.</p>
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * 요청마다 Request ID를 MDC 및 응답 헤더에 설정하고 요청 처리 시간을 측정하여 로깅합니다.
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param filterChain 필터 체인
     * @throws ServletException 서블릿 예외
     * @throws IOException 입출력 예외
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader(REQUEST_ID_HEADER));
        long startedAt = System.nanoTime();

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("request completed: method={}, path={}, status={}, durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMillis);
            } else if (status >= 400) {
                log.warn("request completed: method={}, path={}, status={}, durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMillis);
            } else if (log.isDebugEnabled()) {
                log.debug("request completed: method={}, path={}, status={}, durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, elapsedMillis);
            }
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    /**
     * 헤더의 Request ID 유효성을 검증하고, 없을 경우 새로운 UUID를 생성합니다.
     *
     * @param candidate 후보 Request ID 문자열
     * @return 정규화된 Request ID
     */
    private static String normalizeRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
