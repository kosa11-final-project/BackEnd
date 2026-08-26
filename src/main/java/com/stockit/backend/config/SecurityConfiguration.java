package com.stockit.backend.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.auth.dto.response.AuthUserResponse;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.auth.security.InternalApiKeyAuthenticationFilter;
import com.stockit.backend.feature.auth.security.JsonLoginAuthenticationFilter;
import com.stockit.backend.feature.auth.service.AuthService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class SecurityConfiguration {

    private static final String ADMIN_ROLE = "GREENFOOD_ADMIN";
    private static final String DEMAND_FORECAST_IMPORT_URL =
            "/api/v1/demand-forecasts/import";

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiSecurityFilterChain(
            HttpSecurity http,
            InternalApiProperties properties,
            ObjectMapper objectMapper
    ) throws Exception {
        InternalApiKeyAuthenticationFilter authenticationFilter =
                new InternalApiKeyAuthenticationFilter(properties, objectMapper);

        http
                .securityMatcher(DEMAND_FORECAST_IMPORT_URL)
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authenticationException) ->
                                writeAuthenticationError(request, response, objectMapper))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeAuthenticationError(request, response, objectMapper)))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JSON API에서 사용할 쿠키 기반 세션 인증을 설정하는 클래스
     *
     * <p>로그인과 로그아웃은 보안 필터가 처리하고, {@code AuthController}는
     * CSRF 토큰 발급과 현재 사용자 조회를 담당함</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper,
            AuthService authService,
            SessionRegistry sessionRegistry,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            CookieCsrfTokenRepository csrfTokenRepository,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        // Spring 기본 폼 로그인 대신 프론트엔드의 JSON 요청을 읽는 별도 필터 등록
        JsonLoginAuthenticationFilter loginFilter = new JsonLoginAuthenticationFilter(objectMapper);
        loginFilter.setAuthenticationManager(authenticationManager);
        loginFilter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
        loginFilter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        loginFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
            authService.recordSuccessfulLogin(principal.getUserId());
            // 인증 전 토큰의 재사용을 막기 위해 로그인 성공 시 CSRF 토큰 재발급
            csrfTokenRepository.saveToken(
                    csrfTokenRepository.generateToken(request),
                    request,
                    response
            );
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.of(AuthUserResponse.from(principal)), objectMapper);
        });
        // 계정 없음, 비밀번호 오류, 비활성 상태를 구분하지 않고 동일한 401 응답 반환
        loginFilter.setAuthenticationFailureHandler((request, response, exception) ->
                writeAuthenticationError(request, response, objectMapper));

        // 새 로그인으로 만료된 기존 세션도 일반 인증 실패와 동일하게 처리
        SessionInformationExpiredStrategy expiredStrategy = event ->
                writeAuthenticationError(event.getRequest(), event.getResponse(), objectMapper);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authenticationException) ->
                                writeAuthenticationError(request, response, objectMapper))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(request, response, ErrorCode.FORBIDDEN, objectMapper)))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        // 서버 세션과 브라우저의 인증 관련 쿠키를 함께 제거
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) ->
                                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.empty(), objectMapper)))
                .authorizeHttpRequests(authorize -> authorize
                        // SSE는 최초 REQUEST에서 인증을 마친 뒤 완료·타임아웃 시 ASYNC로
                        // 재디스패치된다. 이미 커밋된 스트림을 다시 권한 심사하지 않는다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        // 로그인 준비 API와 API 문서만 인증 없이 접근 허용
                        .requestMatchers(
                                JsonLoginAuthenticationFilter.LOGIN_URL,
                                "/api/v1/auth/csrf",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        // 현재 버전의 모든 업무 API는 전체 총괄만 접근 가능
                        .requestMatchers("/api/**").hasRole(ADMIN_ROLE)
                        .anyRequest().permitAll())
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAt(new ConcurrentSessionFilter(sessionRegistry, expiredStrategy), ConcurrentSessionFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure}") boolean secure
    ) {
        // 프론트엔드가 쿠키 값을 헤더로 복사할 수 있도록 XSRF-TOKEN은 노출하고,
        // 세션 ID가 JavaScript에서 노출되지 않도록 JSESSIONID는 HttpOnly로 보호함
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .secure(secure));
        return repository;
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
            SessionRegistry sessionRegistry,
            CookieCsrfTokenRepository csrfTokenRepository
    ) {
        // 동일 사용자가 새로 로그인하면 신규 로그인을 허용하고 기존 세션은 만료함
        ConcurrentSessionControlAuthenticationStrategy concurrentStrategy =
                new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
        concurrentStrategy.setMaximumSessions(1);
        concurrentStrategy.setExceptionIfMaximumExceeded(false);

        // 동시 세션 수 확인 → 세션 ID 및 CSRF 토큰 교체 → 최종 세션 등록 순으로 실행
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrentStrategy,
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)
        ));
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins
    ) {
        // 인증 쿠키가 포함되므로 '*'를 사용할 수 없으며, 설정된 프론트엔드 출처만 허용함
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.HEAD.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-XSRF-TOKEN"
        ));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void writeAuthenticationError(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper objectMapper
    ) throws IOException {
        writeError(request, response, ErrorCode.AUTHENTICATION_FAILED, objectMapper);
    }

    private static void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            ObjectMapper objectMapper
    ) throws IOException {
        writeJson(
                response,
                errorCode.getHttpStatus().value(),
                ApiErrorResponse.of(errorCode, request.getRequestURI()),
                objectMapper
        );
    }

    private static void writeJson(
            HttpServletResponse response,
            int status,
            Object body,
            ObjectMapper objectMapper
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
