package com.stockit.backend.feature.inventory.domain;

import java.util.List;

/** 통합재고 화면에서 사용하는 정규화 판매 채널 코드입니다. */
public enum InventoryChannelType {
    GREETING,
    ECOMMERCE,
    HYUNDAI_DEPT,
    HMART;

    private static final List<String> CODES = List.of(values()).stream()
            .map(Enum::name)
            .toList();

    public static List<String> codes() {
        return CODES;
    }

    public static boolean supports(String code) {
        return CODES.contains(code);
    }
}
