package com.stockit.backend.feature.inventory.dto.request;

import java.util.Set;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 목록·요약 조회가 조용히 무시하는 오타 query parameter를 차단합니다.
 * 두 endpoint가 같은 request contract를 사용하므로 허용 목록도 공유합니다.
 */
public final class InventoryQueryParameterValidator {

    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "q",
            "channelType",
            "salesPointCode",
            "warehouseCode",
            "regionCode",
            "categoryId",
            "storageType",
            "riskGrade",
            "assessmentStatus",
            "filterOperator",
            "page",
            "size",
            "sort"
    );

    private InventoryQueryParameterValidator() {
    }

    public static void validate(HttpServletRequest request) {
        boolean hasUnknownParameter = request.getParameterMap().keySet().stream()
                .anyMatch(parameter -> !ALLOWED_PARAMETERS.contains(parameter));
        if (hasUnknownParameter) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }
}
