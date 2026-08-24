package com.stockit.backend.feature.strategy.controller;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.auth.security.AuthPrincipal;
import com.stockit.backend.feature.strategy.domain.StrategyCaseCreated;
import com.stockit.backend.feature.strategy.dto.request.AiStrategyCaseListRequest;
import com.stockit.backend.feature.strategy.dto.request.AdjustAiStrategySimulationRequest;
import com.stockit.backend.feature.strategy.dto.request.AiStrategyCaseListQueryParameterValidator;
import com.stockit.backend.feature.strategy.dto.request.CreateAiStrategyRequest;
import com.stockit.backend.feature.strategy.dto.request.SendAiStrategyTeamsRequest;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseListPageResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseResponse;
import com.stockit.backend.feature.strategy.dto.response.AdjustedAiStrategySimulationResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyReviewerListResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyTeamsRequestResponse;
import com.stockit.backend.feature.strategy.service.AiStrategyApprovalService;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseListService;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseQueryService;
import com.stockit.backend.feature.strategy.service.AiStrategyReviewerService;
import com.stockit.backend.feature.strategy.service.StrategyCaseService;
import com.stockit.backend.feature.strategy.simulation.StrategyAdjustmentSimulationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** AI 전략 생성 요청·목록·상세·조정 시뮬레이션 API */
@RestController
@RequestMapping("/api/v1/ai-strategies")
@Tag(name = "AI 전략 생성", description = "비동기 AI 전략 생성을 요청하고 진행 상태와 결과를 조회합니다.")
public class AiStrategyController {

    private final StrategyCaseService caseService;
    private final AiStrategyCaseQueryService queryService;
    private final AiStrategyCaseListService listService;
    private final StrategyAdjustmentSimulationService adjustmentSimulationService;
    private final AiStrategyReviewerService reviewerService;
    private final AiStrategyApprovalService approvalService;

    public AiStrategyController(
            StrategyCaseService caseService,
            AiStrategyCaseQueryService queryService,
            AiStrategyCaseListService listService,
            StrategyAdjustmentSimulationService adjustmentSimulationService,
            AiStrategyReviewerService reviewerService,
            AiStrategyApprovalService approvalService
    ) {
        this.caseService = caseService;
        this.queryService = queryService;
        this.listService = listService;
        this.adjustmentSimulationService = adjustmentSimulationService;
        this.reviewerService = reviewerService;
        this.approvalService = approvalService;
    }

    @GetMapping
    @Operation(
            summary = "AI 전략 생성 Case 목록 조회",
            description = "최종 선택 전 생성 Case를 조회합니다. 생성 상태별 건수는 현재 상태 필터를 제외한 동일 검색·기간 조건으로 집계합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "목록 조회 조건 오류", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ApiResponse<AiStrategyCaseListPageResponse> list(
            HttpServletRequest httpRequest,
            @Valid @ParameterObject @ModelAttribute AiStrategyCaseListRequest request
    ) {
        AiStrategyCaseListQueryParameterValidator.validate(httpRequest);
        return ApiResponse.of(listService.findAll(request.toQuery()));
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

    /** Case 정보와 생성 당시 조건, 만료되지 않은 추천 결과의 상세 조회 */
    @GetMapping("/{strategyCaseId}")
    @Operation(summary = "AI 전략 생성 상태·결과 조회", description = "Case·상품·요청 조건을 반환하고, 생성 완료 후에는 표시명이 보강된 기준 시뮬레이션과 추천 전략을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Case 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "생성 결과 보관 기간 만료", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ApiResponse<AiStrategyCaseResponse> detail(@PathVariable Long strategyCaseId) {
        return ApiResponse.of(queryService.find(strategyCaseId));
    }

    @PostMapping("/{strategyCaseId}/candidates/{candidateId}/simulations")
    @Operation(
            summary = "AI 전략 조건 조정 시뮬레이션",
            description = "생성 당시 계산 스냅샷에서 적용 수량·할인율·기간을 변경하고 서버 계산을 동기 재실행합니다. 원본 추천 결과는 변경하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조정 시뮬레이션 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "실행 불가능한 조정 조건", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Case 또는 후보 없음", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Redis 계산 스냅샷 만료", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ApiResponse<AdjustedAiStrategySimulationResponse> adjustSimulation(
            @PathVariable Long strategyCaseId,
            @PathVariable String candidateId,
            @Valid @RequestBody AdjustAiStrategySimulationRequest request,
            @Parameter(description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
            @RequestHeader(name = "X-XSRF-TOKEN") String csrfToken
    ) {
        return ApiResponse.of(adjustmentSimulationService.simulate(
                strategyCaseId,
                candidateId,
                request.toCommand()
        ));
    }

    @GetMapping("/reviewers")
    @Operation(
            summary = "AI 전략 Teams 검토자 목록 조회",
            description = "현재 사용자와 동일한 조직의 활성 이메일 보유 사용자를 조회합니다."
    )
    public ApiResponse<AiStrategyReviewerListResponse> reviewers(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        AuthPrincipal authenticated = requirePrincipal(principal);
        return ApiResponse.of(reviewerService.findAll(
                authenticated.getOrganizationId()
        ));
    }

    @PostMapping("/{strategyCaseId}/teams-requests")
    @Operation(
            summary = "최종 AI 전략 선택 및 Teams 검토 요청",
            description = "선택 후보와 계산 스냅샷을 DB에 확정하고 Reviewer별 Teams 개인 채팅을 전송합니다."
    )
    public ApiResponse<AiStrategyTeamsRequestResponse> sendToTeams(
            @PathVariable Long strategyCaseId,
            @Valid @RequestBody SendAiStrategyTeamsRequest request,
            @Parameter(description = "GET /api/v1/auth/csrf에서 발급받은 CSRF 토큰")
            @RequestHeader(name = "X-XSRF-TOKEN") String csrfToken,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        AuthPrincipal authenticated = requirePrincipal(principal);
        return ApiResponse.of(approvalService.sendToTeams(
                strategyCaseId,
                request.optionId(),
                request.reviewerIds(),
                authenticated.getUserId(),
                authenticated.getUserName(),
                authenticated.getOrganizationId()
        ));
    }

    private static AuthPrincipal requirePrincipal(AuthPrincipal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.AUTHENTICATION_FAILED);
        }
        return principal;
    }
}
