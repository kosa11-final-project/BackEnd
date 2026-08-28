package com.stockit.backend.feature.inventory.dto.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.util.StringUtils;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 통합 재고 목록과 요약 조회에 공통으로 사용하는 HTTP query parameter입니다.
 * 목록 화면의 URL 상태를 그대로 바인딩하되, DB 조회에는 {@link InventoryQuery}만 전달합니다.
 */
public class InventoryQueryRequest {

    private static final List<String> CHANNEL_TYPES = List.of("GREETING", "ECOMMERCE", "HYUNDAI_DEPT", "HMART");
    private static final List<String> STORAGE_TYPES = List.of("FROZEN", "COLD", "ROOM_TEMP");
    private static final List<String> RISK_GRADES = List.of("GOOD", "NORMAL", "WARNING", "CRITICAL");
    private static final List<String> ASSESSMENT_STATUSES = List.of("ASSESSED", "UNASSESSED");
    private static final List<String> FILTER_OPERATORS = List.of("AND", "OR");

    @Schema(description = "상품·SKU·판매처 검색어", example = "만두")
    @Size(max = 100, message = "검색어는 100자 이내여야 합니다.")
    private String q;

    @Schema(description = "정규화된 판매 채널", allowableValues = {"GREETING", "ECOMMERCE", "HYUNDAI_DEPT", "HMART"})
    private List<String> channelType = new ArrayList<>();
    @Schema(description = "소유 판매처 업무 코드, 반복 가능", example = "GREETING")
    private List<String> salesPointCode = new ArrayList<>();
    @Schema(description = "물류센터 업무 코드, 반복 가능", example = "GYEONGIN_1")
    private List<String> warehouseCode = new ArrayList<>();
    @Schema(description = "판매처 지역 코드, 반복 가능", example = "CAPITAL")
    private List<String> regionCode = new ArrayList<>();
    @Schema(description = "카테고리 ID", example = "12")
    private String categoryId;
    @Schema(description = "카테고리 ID 목록. filterOperator에 따라 모든 값(AND) 또는 하나 이상(OR)을 만족", example = "[12, 18]")
    private List<String> categoryIds = new ArrayList<>();
    @Schema(description = "보관 유형", allowableValues = {"FROZEN", "COLD", "ROOM_TEMP"})
    private List<String> storageType = new ArrayList<>();
    @Schema(description = "위험 등급", allowableValues = {"GOOD", "NORMAL", "WARNING", "CRITICAL"})
    private List<String> riskGrade = new ArrayList<>();
    @Schema(description = "위험 판정 상태", allowableValues = {"ASSESSED", "UNASSESSED"})
    private List<String> assessmentStatus = new ArrayList<>();
    @Schema(description = "안전재고 미달 여부. Y이면 판매 가능 재고가 안전재고보다 적은 범위만 조회", allowableValues = {"Y", "N"})
    private String shortageYn;
    @Schema(
            description = "활성 필터 조건 결합 방식. AND는 선택한 조건을 모두 만족하고 OR는 하나 이상 만족",
            allowableValues = {"AND", "OR"},
            defaultValue = "AND"
    )
    private String filterOperator = "AND";

    @Schema(description = "1부터 시작하는 페이지 번호", example = "1", defaultValue = "1", minimum = "1")
    @Min(value = 1, message = "page는 1 이상이어야 합니다.")
    private Integer page = 1;

    @Schema(description = "페이지 크기", example = "20", defaultValue = "20", minimum = "1", maximum = "100")
    @Min(value = 1, message = "size는 1 이상이어야 합니다.")
    @Max(value = 100, message = "size는 100 이하이어야 합니다.")
    private Integer size = 20;

    @Schema(description = "허용 정렬 field,direction", example = "updatedAt,desc", defaultValue = "updatedAt,desc")
    private String sort = "updatedAt,desc";

    public InventoryQuery toQuery(LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new IllegalArgumentException("asOfDate must not be null");
        }
        validatePagination();

        return new InventoryQuery(
                trimToNull(q),
                normalize(channelType, CHANNEL_TYPES, "channelType"),
                normalize(salesPointCode, null, "salesPointCode"),
                normalize(warehouseCode, null, "warehouseCode"),
                normalize(regionCode, null, "regionCode"),
                parseCategoryIds(),
                normalize(storageType, STORAGE_TYPES, "storageType"),
                normalize(riskGrade, RISK_GRADES, "riskGrade"),
                normalize(assessmentStatus, ASSESSMENT_STATUSES, "assessmentStatus"),
                normalizeShortageYn(),
                normalizeFilterOperator(),
                page == null ? 1 : page,
                size == null ? 20 : size,
                sortColumn(sort),
                sortDirection(sort),
                asOfDate,
                true
        );
    }

    private void validatePagination() {
        if ((page != null && page < 1) || (size != null && (size < 1 || size > 100))) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private List<Long> parseCategoryIds() {
        List<String> rawValues = new ArrayList<>();
        if (categoryIds != null) rawValues.addAll(categoryIds);
        if (rawValues.isEmpty() && categoryId != null) rawValues.add(categoryId);

        return normalize(rawValues, null, "categoryIds").stream()
                .map(value -> {
                    try {
                        long parsed = Long.parseLong(value);
                        if (parsed < 1) throw new NumberFormatException();
                        return parsed;
                    } catch (NumberFormatException exception) {
                        throw new AppException(ErrorCode.INVALID_PARAMETER);
                    }
                })
                .distinct()
                .toList();
    }

    private static List<String> normalize(List<String> values, List<String> allowed, String field) {
        if (values == null) {
            return List.of();
        }

        List<String> normalized = values.stream()
                .map(InventoryQueryRequest::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .toList();

        if (allowed != null && normalized.stream().anyMatch(value -> !allowed.contains(value))) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }

        if (normalized.size() > 50) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }

        return normalized;
    }

    private static String sortColumn(String sort) {
        String field = sortPart(sort, 0, "updatedAt");
        return switch (field) {
            case "updatedAt" -> "updated_at";
            case "productName" -> "product_name";
            case "skuCode" -> "sku_code";
            case "currentQuantity", "currentQty" -> "current_qty";
            case "availableQuantity", "availableQty" -> "available_qty";
            case "reservedQuantity", "reservedQty" -> "reserved_qty";
            case "expectedDisposalQuantity", "expectedDisposalQty" -> "expected_disposal_qty";
            case "riskGrade" -> "risk_grade";
            case "shortageYn" -> "shortage_yn";
            case "nearestExpiryDays", "expiryDate", "nearestExpiryDate", "expiryDays" -> "nearest_expiry_days";
            default -> throw new AppException(ErrorCode.INVALID_PARAMETER);
        };
    }

    private static String sortDirection(String sort) {
        String direction = sortPart(sort, 1, "desc").toLowerCase(Locale.ROOT);
        return switch (direction) {
            case "asc" -> "ASC";
            case "desc" -> "DESC";
            default -> throw new AppException(ErrorCode.INVALID_PARAMETER);
        };
    }

    private static String sortPart(String sort, int index, String fallback) {
        if (!StringUtils.hasText(sort)) {
            return fallback;
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2 || index >= parts.length) {
            if (index == 1 && parts.length == 1) {
                return fallback;
            }
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        if (!StringUtils.hasText(parts[index])) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        return parts[index].trim();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeShortageYn() {
        String normalized = trimToNull(shortageYn);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("Y", "N").contains(normalized)) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        return normalized;
    }

    private String normalizeFilterOperator() {
        String normalized = trimToNull(filterOperator);
        if (normalized == null) {
            return "AND";
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!FILTER_OPERATORS.contains(normalized)) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        return normalized;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public List<String> getChannelType() {
        return channelType;
    }

    public void setChannelType(List<String> channelType) {
        this.channelType = channelType;
    }

    public List<String> getSalesPointCode() {
        return salesPointCode;
    }

    public void setSalesPointCode(List<String> salesPointCode) {
        this.salesPointCode = salesPointCode;
    }

    public List<String> getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(List<String> warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public List<String> getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(List<String> regionCode) {
        this.regionCode = regionCode;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public List<String> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<String> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public List<String> getStorageType() {
        return storageType;
    }

    public void setStorageType(List<String> storageType) {
        this.storageType = storageType;
    }

    public List<String> getRiskGrade() {
        return riskGrade;
    }

    public void setRiskGrade(List<String> riskGrade) {
        this.riskGrade = riskGrade;
    }

    public List<String> getAssessmentStatus() {
        return assessmentStatus;
    }

    public void setAssessmentStatus(List<String> assessmentStatus) {
        this.assessmentStatus = assessmentStatus;
    }

    public String getShortageYn() {
        return shortageYn;
    }

    public void setShortageYn(String shortageYn) {
        this.shortageYn = shortageYn;
    }

    public String getFilterOperator() {
        return filterOperator;
    }

    public void setFilterOperator(String filterOperator) {
        this.filterOperator = filterOperator;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }
}
