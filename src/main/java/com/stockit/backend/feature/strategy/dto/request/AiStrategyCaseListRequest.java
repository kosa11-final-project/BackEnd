package com.stockit.backend.feature.strategy.dto.request;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class AiStrategyCaseListRequest {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private static final Set<String> VISIBLE_STATUSES = Set.of(
            "GENERATING", "GENERATED", "GENERATION_FAILED"
    );

    @Schema(description = "0부터 시작하는 페이지 번호", example = "0", defaultValue = "0")
    @Min(value = 0, message = "page는 0 이상이어야 합니다.")
    private Integer page = DEFAULT_PAGE;

    @Schema(description = "페이지 크기", example = "10", defaultValue = "10", maximum = "100")
    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = MAX_SIZE, message = "size는 100 이하이어야 합니다.")
    private Integer size = DEFAULT_SIZE;

    @Schema(description = "Case ID, 전략명, SKU 코드·명 또는 상품명 검색", maxLength = 100)
    @Size(max = 100, message = "검색어는 100자 이내여야 합니다.")
    private String query;

    @Schema(
            description = "생성 상태. ALL 또는 미입력 시 전체",
            allowableValues = {"ALL", "GENERATING", "GENERATED", "GENERATION_FAILED"}
    )
    private String status;

    @Schema(description = "생성 요청일 시작(포함)", example = "2026-08-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @Schema(description = "생성 요청일 종료(포함)", example = "2026-08-24")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    @Schema(description = "생성 요청일 정렬", example = "createdAt,desc", defaultValue = "createdAt,desc")
    private String sort = "createdAt,desc";

    public AiStrategyCaseListQuery toQuery() {
        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        if (normalizedPage < 0 || normalizedSize < 1 || normalizedSize > MAX_SIZE) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }

        String normalizedStatus = upperToNull(status);
        if ("ALL".equals(normalizedStatus)) {
            normalizedStatus = null;
        }
        if (normalizedStatus != null && !VISIBLE_STATUSES.contains(normalizedStatus)) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }

        String rawQuery = trimToNull(query);
        Long strategyCaseId = parsePositiveLong(rawQuery);
        String escapedQuery = escapeLike(rawQuery);
        try {
            return new AiStrategyCaseListQuery(
                    normalizedPage,
                    normalizedSize,
                    escapedQuery,
                    strategyCaseId,
                    normalizedStatus,
                    from == null ? null : from.atStartOfDay(),
                    to == null ? null : to.plusDays(1).atStartOfDay(),
                    sortDirection(sort)
            );
        } catch (DateTimeException exception) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private static String sortDirection(String value) {
        if (!StringUtils.hasText(value)) {
            return "DESC";
        }
        String[] parts = value.trim().split(",", -1);
        if (parts.length != 2 || !"createdAt".equals(parts[0].trim())) {
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

    private static Long parsePositiveLong(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
