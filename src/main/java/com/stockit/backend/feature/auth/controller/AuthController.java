package com.stockit.backend.feature.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.auth.dto.response.AuthUserResponse;
import com.stockit.backend.feature.auth.dto.response.CsrfTokenResponse;
import com.stockit.backend.feature.auth.security.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Spring Security가 관리하는 인증 상태를 외부에 제공하는 컨트롤러
 *
 * <p>로그인과 로그아웃은 Spring MVC에 도달하기 전에 보안 필터에서 처리하며,
 * 이 컨트롤러는 CSRF 토큰 발급과 현재 사용자 조회만 담당함</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "세션 인증 상태와 CSRF 토큰을 관리합니다.")
public class AuthController {

    @Operation(
            summary = "CSRF 토큰 발급",
            description = "로그인 등 상태 변경 요청에 사용할 CSRF 토큰을 반환하고 XSRF-TOKEN 쿠키를 설정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "CSRF 토큰 발급 성공"
            )
    })
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        // 토큰 값과 프론트엔드가 사용할 헤더 이름을 함께 전달
        return ApiResponse.of(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @Operation(
            summary = "현재 로그인 사용자 조회",
            description = "JSESSIONID에 연결된 세션에서 현재 사용자 정보를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "현재 사용자 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "세션이 없거나 만료됨",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        // 별도 DB 조회 없이 현재 세션에 저장된 사용자 정보 반환
        return ApiResponse.of(AuthUserResponse.from(principal));
    }
}
