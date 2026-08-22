package com.stockit.backend.feature.strategy.dto.request;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.domain.StrategyType;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class StrategyExecutionListRequest {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private static final Map<String, String> CASE_STATUSES = Map.of(
            "READY", "READY_TO_EXECUTE",
            "EXECUTING", "EXECUTING",
            "COMPLETED", "EXECUTION_COMPLETED"
    );
    private static final Set<String> ACTION_TYPES = Set.of(
            StrategyType.REALLOCATION.name(),
            StrategyType.RT_TRANSFER.name(),
            StrategyType.PRICE_DISCOUNT.name(),
            StrategyType.PROMOTION_STOP.name(),
            StrategyType.CHANNEL_EXPANSION.name(),
            StrategyType.CHANNEL_CONCENTRATION.name(),
            StrategyType.REPLENISHMENT_REQUEST.name(),
            StrategyType.SAFETY_STOCK_ADJUSTMENT.name()
    );

    @Schema(description = "0부터 시작하는 페이지 번호", example = "0", defaultValue = "0", minimum = "0")
    @Min(value = 0, message = "page는 0 이상이어야 합니다.")
    private Integer page = DEFAULT_PAGE;

    @Schema(description = "페이지 크기", example = "10", defaultValue = "10", minimum = "1", maximum = "100")
    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = MAX_SIZE, message = "size는 100 이하이어야 합니다.")
    private Integer size = DEFAULT_SIZE;

    @Schema(description = "전략 번호 또는 상품명 검색어", example = "두부", maxLength = 100)
    @Size(max = 100, message = "검색어는 100자 이내여야 합니다.")
    private String query;

    @Schema(description = "전략 실행 상태", allowableValues = {"READY", "EXECUTING", "COMPLETED"})
    private String status;

    @Schema(
            description = "전략에 포함된 액션 유형",
            allowableValues = {
                    "REALLOCATION", "RT_TRANSFER", "PRICE_DISCOUNT", "PROMOTION_STOP",
                    "CHANNEL_EXPANSION", "CHANNEL_CONCENTRATION", "REPLENISHMENT_REQUEST",
                    "SAFETY_STOCK_ADJUSTMENT"
            }
    )
    private String actionType;

    @Schema(description = "정렬 조건", example = "establishedAt,desc", defaultValue = "establishedAt,desc")
    private String sort = "establishedAt,desc";

    public StrategyExecutionQuery toQuery() {
        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedPage < 0 || normalizedSize < 1 || normalizedSize > MAX_SIZE) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }

        String normalizedStatus = upperToNull(status);
        String caseStatus = normalizedStatus == null ? null : CASE_STATUSES.get(normalizedStatus);
        if (normalizedStatus != null && caseStatus == null) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }

        String normalizedActionType = upperToNull(actionType);
        if (normalizedActionType != null && !ACTION_TYPES.contains(normalizedActionType)) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }

        return new StrategyExecutionQuery(
                normalizedPage,
                normalizedSize,
                trimToNull(query),
                caseStatus,
                normalizedActionType,
                sortDirection(sort)
        );
    }

    private static String sortDirection(String value) {
        if (!StringUtils.hasText(value)) {
            return "DESC";
        }
        String[] parts = value.trim().split(",", -1);
        if (parts.length != 2 || !"establishedAt".equals(parts[0].trim())) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        return switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> "ASC";
            case "desc" -> "DESC";
            default -> throw new AppException(ErrorCode.INVALID_PARAMETER);
        };
    }

    private static String upperToNull(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
