package com.stockit.backend.feature.inventory.dto.response;

/** 카테고리 경로를 구성하는 한 단계의 응답입니다. */
public record CategoryPathItemResponse(
        Long id,
        String name,
        Integer level
) {
}
