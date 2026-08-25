package com.stockit.backend.feature.auth.security;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.feature.auth.dto.request.LoginRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@value #LOGIN_URL}으로 전달된 JSON 로그인 요청을 인증하는 필터
 *
 * <p>인증 성공 및 실패 응답은 {@code SecurityConfiguration}에서 공통으로 설정하며,
 * 이 필터는 요청 본문을 Spring Security 인증 요청으로 변환하는 역할만 담당함</p>
 */
public class JsonLoginAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    public static final String LOGIN_URL = "/api/v1/auth/login";

    private final ObjectMapper objectMapper;

    public JsonLoginAuthenticationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        setFilterProcessesUrl(LOGIN_URL);
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws AuthenticationException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            throw new AuthenticationServiceException("Authentication method not supported");
        }

        try {
            LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);
            // 로그인 ID의 불필요한 앞뒤 공백만 제거하고 비밀번호는 원문 유지
            String loginId = loginRequest.loginId() == null ? "" : loginRequest.loginId().trim();
            String password = loginRequest.password() == null ? "" : loginRequest.password();

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.unauthenticated(loginId, password);
            // IP와 기존 세션 ID 등 요청 부가 정보를 인증 객체에 함께 기록
            setDetails(request, authentication);
            return getAuthenticationManager().authenticate(authentication);
        } catch (IOException exception) {
            throw new AuthenticationServiceException("Invalid authentication request", exception);
        }
    }
}
