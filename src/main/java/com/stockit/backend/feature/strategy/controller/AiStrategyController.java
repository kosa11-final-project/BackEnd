package com.stockit.backend.feature.strategy.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.dto.request.CreateAiStrategyRequest;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseQueryService;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ai-strategies")
@Tag(name = "AI 전략 생성", description = "비동기 AI 전략 생성을 요청하고 진행 상태와 결과를 조회합니다.")
public class AiStrategyController {

    private final StrategyCaseService caseService;
    private final AiStrategyCaseQueryService queryService;

    public AiStrategyController(
            StrategyCaseService caseService,
            AiStrategyCaseQueryService queryService
    ) {
        this.caseService = caseService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "AI 전략 생성 요청", description = "Case 저장 후 RabbitMQ에 작업을 발행하고 즉시 202를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "생성 요청 접수"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 조건 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<StrategyCaseCreated>> create(
            @Valid @RequestBody CreateAiStrategyRequest request,
            @Parameter(description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
            @RequestHeader(name = "X-XSRF-TOKEN") String csrfToken,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        StrategyCaseCreated created = caseService.createStrategyCase(
                request.toCommand(), principal == null ? null : principal.getUserId()
        );
        URI location = URI.create("/api/v1/ai-strategies/" + created.strategyCaseId());
        return ResponseEntity.accepted().location(location).body(ApiResponse.of(created));
    }

    @GetMapping("/{strategyCaseId}")
    @Operation(summary = "AI 전략 생성 상태·결과 조회", description = "생성 중에는 상태만, 완료 후에는 기준 시뮬레이션과 추천 전략을 반환합니다.")
    public ApiResponse<AiStrategyCaseResponse> detail(@PathVariable Long strategyCaseId) {
        return ApiResponse.of(queryService.find(strategyCaseId));
    }
}
