package com.stockit.backend.feature.auth.security;

import static com.stockit.backend.feature.auth.security.InternalApiSecurityConstants.INTERNAL_API_KEY_HEADER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.config.InternalApiProperties;
import com.stockit.backend.feature.auth.vo.AuthUserVO;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SERVICE_ROLE = "ML_SERVICE";

    private final InternalApiProperties properties;
    private final ObjectMapper objectMapper;

    public InternalApiKeyAuthenticationFilter(
            InternalApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.isConfigured()) {
            rejectAuthentication(request, response);
            return;
        }

        String providedKey = request.getHeader(INTERNAL_API_KEY_HEADER);
        if (!matches(providedKey, properties.key())) {
            rejectAuthentication(request, response);
            return;
        }

        AuthPrincipal principal = createPrincipal();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private AuthPrincipal createPrincipal() {
        AuthUserVO serviceUser = new AuthUserVO();
        serviceUser.setUserId(properties.userId());
        serviceUser.setLoginId(properties.principalName());
        serviceUser.setUserName(properties.principalName());
        serviceUser.setRoleCode(INTERNAL_SERVICE_ROLE);
        return AuthPrincipal.from(serviceUser);
    }

    private static boolean matches(String providedKey, String expectedKey) {
        if (providedKey == null || providedKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void rejectAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        SecurityContextHolder.clearContext();
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_FAILED;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(errorCode, request.getRequestURI())
        );
    }
}
