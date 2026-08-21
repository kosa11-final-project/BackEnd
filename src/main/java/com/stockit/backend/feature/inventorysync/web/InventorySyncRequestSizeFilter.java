package com.stockit.backend.feature.inventorysync.web;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventorysync.InventorySyncRoutes;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 동기화 시작 요청은 body 없이 clientRequestId만 받으므로 8KiB를 넘기면 읽기 전에 차단합니다. */
@Component
public class InventorySyncRequestSizeFilter extends OncePerRequestFilter {
    static final String SYNC_PATH = InventorySyncRoutes.RUNS;
    static final String DEMO_PATH = InventorySyncRoutes.DEMO_ADJUSTMENTS;
    static final long MAX_SYNC_BODY_BYTES = 8 * 1024L;
    static final long MAX_DEMO_BODY_BYTES = 256 * 1024L;
    private final ObjectMapper objectMapper;

    public InventorySyncRequestSizeFilter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long maxBody = request.getRequestURI().equals(SYNC_PATH)
                ? MAX_SYNC_BODY_BYTES
                : request.getRequestURI().equals(DEMO_PATH)
                    ? MAX_DEMO_BODY_BYTES
                    : -1;
        if (maxBody > 0 && "POST".equalsIgnoreCase(request.getMethod())) {
            if (request.getContentLengthLong() > maxBody) {
                reject(request, response);
                return;
            }
            if (request.getContentLengthLong() < 0) {
                byte[] body = readBounded(request, maxBody);
                if (body == null) {
                    reject(request, response);
                    return;
                }
                filterChain.doFilter(new CachedBodyRequest(request, body), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static byte[] readBounded(HttpServletRequest request, long maxBody) throws IOException {
        int limit = Math.toIntExact(maxBody + 1);
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.min(limit, 8192));
        try (var input = request.getInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (body.size() + read > limit) return null;
                body.write(buffer, 0, read);
            }
        }
        return body.size() > maxBody ? null : body.toByteArray();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.PAYLOAD_TOO_LARGE.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                ErrorCode.PAYLOAD_TOO_LARGE, "동기화 요청 본문이 허용된 크기를 초과했습니다.", request.getRequestURI()));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private CachedBodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
    }
}
