package com.stockit.backend.feature.strategy.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiErrorResponse;
import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.strategy.dto.response.StrategyExecutionResponse;
import com.stockit.backend.feature.strategy.service.StrategyExecutionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

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
            description = "최종 선택된 전략만 반환하며 지원 액션 4종을 한 번에 조회합니다. 실행 상태와 진행률은 DB에서 확정할 수 없는 경우 null입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
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
    public ApiResponse<List<StrategyExecutionResponse>> list() {
        return ApiResponse.of(strategyExecutionService.findAll());
    }

    @GetMapping("/{strategyCaseId}")
    @Operation(
            summary = "AI 전략 실행 관제 상세 조회",
            description = "strategyCaseId를 기준으로 최종 선택 전략, 복수 액션, 전략 당시와 현재 재고, 선택일 이후 최대 90일의 판매처별 판매량을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세 조회 성공"),
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
