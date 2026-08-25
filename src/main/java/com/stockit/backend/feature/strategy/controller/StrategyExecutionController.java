package com.stockit.backend.feature.strategy.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionResponse;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionPageResponse;
import com.stockit.backend.feature.strategy.dto.request.StrategyExecutionListRequest;
import com.stockit.backend.feature.strategy.dto.request.StrategyExecutionQueryParameterValidator;
import com.stockit.backend.feature.strategy.service.StrategyExecutionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/strategy-executions")
@Tag(name = "AI 전략 실행 관제", description = "최종 선택된 AI 전략과 실제 재고·판매 성과를 조회합니다.")
public class StrategyExecutionController {

    private final StrategyExecutionService strategyExecutionService;

    public StrategyExecutionController(StrategyExecutionService strategyExecutionService) {
        this.strategyExecutionService = strategyExecutionService;
    }

    @GetMapping
    @Operation(
            summary = "AI 전략 실행 관제 목록 조회",
            description = "최종 선택된 전략을 0부터 시작하는 페이지로 반환합니다. 전략 번호·상품명 검색과 실행 상태·포함 액션 유형 필터를 AND로 조합하며, 기본 정렬은 전략 수립일 최신순입니다. size는 최대 100입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "페이지·검색·필터·정렬 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "세션이 없거나 만료됨",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "조회 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ApiResponse<StrategyExecutionPageResponse> list(
            HttpServletRequest httpRequest,
            @Valid @ParameterObject @ModelAttribute StrategyExecutionListRequest request
    ) {
        StrategyExecutionQueryParameterValidator.validate(httpRequest);
        return ApiResponse.of(strategyExecutionService.findAll(request.toQuery()));
    }

    @GetMapping("/{strategyCaseId}")
    @Operation(
            summary = "AI 전략 실행 관제 상세 조회",
            description = "strategyCaseId를 기준으로 최종 선택 전략, 복수 액션, 위치별 재고 변화, 도착 센터와 대상 판매처를 포함한 재고 이동 내역, 전략 실행 기간의 판매량을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "세션이 없거나 만료됨",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "조회 권한이 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "최종 선택된 전략 실행 정보를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public ApiResponse<StrategyExecutionResponse> detail(
            @Parameter(description = "전략 케이스 ID", example = "101")
            @PathVariable Long strategyCaseId
    ) {
        return ApiResponse.of(strategyExecutionService.findByStrategyCaseId(strategyCaseId));
    }
}
