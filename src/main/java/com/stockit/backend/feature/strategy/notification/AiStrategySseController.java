package com.stockit.backend.feature.strategy.notification;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.auth.security.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 인증 사용자가 사이트 내 어느 화면에서도 AI 전략 상태 알림을 받는 SSE API */
@RestController
@RequestMapping("/api/v1/ai-strategies")
@Tag(name = "AI 전략 생성", description = "비동기 AI 전략 생성 상태와 결과를 조회합니다.")
public class AiStrategySseController {

    private final AiStrategySseEmitterRegistry emitterRegistry;

    public AiStrategySseController(AiStrategySseEmitterRegistry emitterRegistry) {
        this.emitterRegistry = emitterRegistry;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "AI 전략 생성 상태 SSE 구독",
            description = "진행 단계는 목록 동기화용으로, 생성 완료·실패는 사용자 알림용으로 전달합니다."
    )
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) UUID clientId,
            HttpServletRequest request
    ) {
        if (principal == null) {
            throw new AppException(ErrorCode.AUTHENTICATION_FAILED);
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AppException(ErrorCode.AUTHENTICATION_FAILED);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(emitterRegistry.subscribe(
                        principal.getUserId(),
                        session.getId(),
                        clientId
                ));
    }
}
