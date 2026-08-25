package com.stockit.backend.feature.inventory.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stockit.backend.common.api.ApiResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryFilterOptionsResponse;
import com.stockit.backend.feature.inventory.service.InventoryQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/inventories/filter-options")
@Tag(name = "재고 필터", description = "통합 재고 조회 화면의 기준정보와 빈 센터 옵션을 제공합니다.")
public class InventoryFilterOptionsController {

    private final InventoryQueryService inventoryQueryService;

    public InventoryFilterOptionsController(InventoryQueryService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    @GetMapping
    @Operation(
            summary = "통합 재고 필터 옵션 조회",
            description = "활성 판매처·센터·지역·카테고리와 현재 데이터에서 확인되는 보관 유형을 반환합니다. 재고가 없는 등록 센터도 REGISTERED_EMPTY로 포함합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "필터 옵션 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 세션이 없음")
    })
    public ApiResponse<InventoryFilterOptionsResponse> get() {
        return ApiResponse.of(inventoryQueryService.filterOptions());
    }
}
