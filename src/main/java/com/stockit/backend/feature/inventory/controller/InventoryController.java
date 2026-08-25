package com.stockit.backend.feature.inventory.controller;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.inventory.dto.request.InventoryQueryRequest;
import com.stockit.backend.feature.inventory.dto.request.InventoryQueryParameterValidator;
import com.stockit.backend.feature.inventory.dto.response.InventoryDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryListResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotsResponse;
import com.stockit.backend.feature.inventory.dto.response.InventorySummaryResponse;
import com.stockit.backend.feature.inventory.service.InventoryQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/inventories")
@Validated
@Tag(name = "재고", description = "SKU 단위 행, 판매처별 재고와 물류센터 미할당 재고를 구분해 제공하는 통합 재고 조회 API")
public class InventoryController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final InventoryQueryService inventoryQueryService;

    public InventoryController(InventoryQueryService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    @GetMapping
    @Operation(
            summary = "통합 재고 목록 조회",
            description = "기존 canonical 재고 테이블을 SKU grain으로 집계하고 판매처 재고는 salesPoints, 판매처에 귀속되지 않은 센터 재고는 unassignedInventory로 분리해 반환합니다. page는 1부터 시작하고 기본 size는 20, 최대 size는 100입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필터·페이지·정렬 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "로그인 세션이 없음",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    public ApiResponse<InventoryListResponse> list(
            HttpServletRequest httpRequest,
            @Valid @ParameterObject @ModelAttribute InventoryQueryRequest request
    ) {
        InventoryQueryParameterValidator.validate(httpRequest);
        return ApiResponse.of(inventoryQueryService.find(request.toQuery(today())));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "통합 재고 요약 조회",
            description = "목록과 동일한 필터 predicate로 KPI 수량을 집계합니다. 목록 요청과 별도로 호출해 상단 KPI를 갱신합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "요약 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필터 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    public ApiResponse<InventorySummaryResponse> summary(
            HttpServletRequest httpRequest,
            @Valid @ParameterObject @ModelAttribute InventoryQueryRequest request
    ) {
        InventoryQueryParameterValidator.validate(httpRequest);
        return ApiResponse.of(inventoryQueryService.summary(request.toQuery(today())));
    }

    @GetMapping("/{skuCode}/sales-points/{salesPointCode}")
    @Operation(
            summary = "선택 재고 상세 조회",
            description = "목록의 SKU 행에서 선택한 skuCode와 salesPointCode 조합으로 판매처별 재고를 반환하고, 별도 unassignedInventory에 판매처 미할당 센터 재고·보관 위치를 제공합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "SKU와 판매처 조합을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    public ApiResponse<InventoryDetailResponse> detail(
            @Parameter(description = "상품 SKU 업무 코드", example = "SKU-4D82A9F1")
            @PathVariable String skuCode,
            @Parameter(description = "소유 판매처 업무 코드", example = "GREETING")
            @PathVariable String salesPointCode
    ) {
        return ApiResponse.of(inventoryQueryService.detail(skuCode, salesPointCode));
    }

    @GetMapping("/{skuCode}/sales-points/{salesPointCode}/lots")
    @Operation(
            summary = "재고 LOT 조회",
            description = "선택 SKU와 판매처의 활성 inventory balance를 LOT 단위로 집계하고 서버에서 FEFO 순위를 계산합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "LOT 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "SKU와 판매처 조합을 찾을 수 없음. 조합은 존재하지만 LOT가 없으면 200과 빈 배열을 반환",
                    content = @Content(schema = @Schema(implementation = com.stockit.backend.common.api.ApiErrorResponse.class))
            )
    })
    public ApiResponse<InventoryLotsResponse> lots(
            @Parameter(description = "상품 SKU 업무 코드", example = "SKU-4D82A9F1")
            @PathVariable String skuCode,
            @Parameter(description = "소유 판매처 업무 코드", example = "GREETING")
            @PathVariable String salesPointCode
    ) {
        return ApiResponse.of(inventoryQueryService.lots(skuCode, salesPointCode));
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }
}
