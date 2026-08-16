package com.stockit.backend.feature.inventory.service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventory.dto.response.CategoryPathItemResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryCategoryResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryItemResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryOptionResponse;
import com.stockit.backend.feature.inventory.dto.response.LocationResponse;
import com.stockit.backend.feature.inventory.dto.response.RiskResponse;
import com.stockit.backend.feature.inventory.dto.response.SalesPointResponse;
import com.stockit.backend.feature.inventory.vo.InventoryItemVO;
import com.stockit.backend.feature.inventory.vo.InventoryLotVO;
import com.stockit.backend.feature.inventory.vo.InventoryOptionVO;

/**
 * DB 조회 결과인 VO 객체를 클라이언트 반환용 DTO/Response 객체로 변환하는 전용 매퍼 컴포넌트
 */
@Component
public class InventoryResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(InventoryResponseMapper.class);
    private static final TypeReference<List<LocationResponse>> LOCATIONS_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<SalesPointResponse>> SALES_POINTS_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    public InventoryResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public InventoryItemResponse toItemResponse(InventoryItemVO item) {
        List<LocationResponse> locations = readJson(item.getLocationsJson(), LOCATIONS_TYPE);
        List<SalesPointResponse> salesPoints = readJson(item.getSalesPointsJson(), SALES_POINTS_TYPE);
        String rowId = item.getSkuCode();

        return new InventoryItemResponse(
                rowId,
                item.getProductCode(),
                item.getProductName(),
                item.getSkuCode(),
                item.getSkuName(),
                item.getImageUrl(),
                item.getCategoryId(),
                item.getCategoryName(),
                toCategoryResponse(item),
                item.getChannelType(),
                item.getSalesPointCode(),
                item.getSalesPointName(),
                item.getStorageType(),
                item.getSellingPrice(),
                item.getCurrentQty(),
                item.getAvailableQty(),
                item.getReservedQty(),
                item.getSafetyQty(),
                item.getInventoryFactState(),
                new RiskResponse(
                        item.getAssessmentStatus() == null ? "UNASSESSED" : item.getAssessmentStatus(),
                        item.getRiskGrade(),
                        item.getRiskReason()
                ),
                locations,
                item.getLocationCount() == null ? locations.size() : item.getLocationCount(),
                salesPoints,
                item.getOwnerSalesPointCount() == null ? salesPoints.size() : item.getOwnerSalesPointCount(),
                item.getLotCount(),
                item.getNearestExpiryDays(),
                item.getNearestExpiryDate() == null ? null : item.getNearestExpiryDate().toLocalDate(),
                item.getDailySales(),
                item.getForecast14Days(),
                item.getUpdatedAt() == null ? null : item.getUpdatedAt().toInstant()
        );
    }

    public InventoryDetailResponse toDetailResponse(InventoryItemVO item, List<InventoryLotResponse> lots) {
        List<LocationResponse> locations = readJson(item.getLocationsJson(), LOCATIONS_TYPE);
        List<SalesPointResponse> salesPoints = readJson(item.getSalesPointsJson(), SALES_POINTS_TYPE);
        String rowId = item.getSkuCode() + ":" + item.getSalesPointCode();

        return new InventoryDetailResponse(
                rowId,
                item.getProductCode(),
                item.getProductName(),
                item.getSkuCode(),
                item.getSkuName(),
                item.getImageUrl(),
                item.getCategoryId(),
                item.getCategoryName(),
                toCategoryResponse(item),
                item.getChannelType(),
                item.getSalesPointCode(),
                item.getSalesPointName(),
                item.getStorageType(),
                item.getSellingPrice(),
                item.getCurrentQty(),
                item.getAvailableQty(),
                item.getReservedQty(),
                item.getSafetyQty(),
                item.getInventoryFactState(),
                new RiskResponse(
                        item.getAssessmentStatus() == null ? "UNASSESSED" : item.getAssessmentStatus(),
                        item.getRiskGrade(),
                        item.getRiskReason()
                ),
                locations,
                item.getLocationCount() == null ? locations.size() : item.getLocationCount(),
                salesPoints,
                item.getLotCount(),
                item.getNearestExpiryDays(),
                item.getNearestExpiryDate() == null ? null : item.getNearestExpiryDate().toLocalDate(),
                item.getDailySales(),
                item.getForecast14Days(),
                item.getUpdatedAt() == null ? null : item.getUpdatedAt().toInstant(),
                lots
        );
    }

    public InventoryCategoryResponse toCategoryResponse(InventoryItemVO item) {
        if (item.getCategoryId() == null || item.getCategoryName() == null || item.getCategoryName().isBlank()) {
            return null;
        }

        List<CategoryPathItemResponse> path = new ArrayList<>();
        addCategoryPathItem(path, item.getGrandparentCategoryId(), item.getGrandparentCategoryName(), item.getGrandparentCategoryLevel());
        addCategoryPathItem(path, item.getParentCategoryId(), item.getParentCategoryName(), item.getParentCategoryLevel());
        addCategoryPathItem(path, item.getCategoryId(), item.getCategoryName(), item.getCategoryLevel());

        CategoryPathItemResponse leaf = new CategoryPathItemResponse(
                item.getCategoryId(), item.getCategoryName(), item.getCategoryLevel()
        );
        return new InventoryCategoryResponse(leaf, path);
    }

    public InventoryLotResponse toLotResponse(InventoryLotVO lot) {
        return new InventoryLotResponse(
                lot.getLotId(),
                lot.getLotNumber(),
                lot.getLotStatus(),
                lot.getQuantity(),
                lot.getAvailableQuantity(),
                lot.getReservedQuantity(),
                toLocalDate(lot.getManufacturedDate()),
                toLocalDate(lot.getReceivedDate()),
                toLocalDate(lot.getExpiryDate()),
                toLocalDate(lot.getSaleStopDate()),
                lot.getExpiryDays(),
                lot.getFefoPriority(),
                lot.getWarehouseCode(),
                lot.getWarehouseName()
        );
    }

    public List<InventoryOptionResponse> mapOptions(List<InventoryOptionVO> options) {
        return options.stream()
                .map(option -> new InventoryOptionResponse(
                        option.getCode(),
                        option.getName(),
                        option.getParentCode(),
                        option.getRegionCode(),
                        option.getChannelType(),
                        option.getAvailability(),
                        option.getCurrentSkuCount(),
                        option.getCurrentBalanceRowCount(),
                        option.getCurrentOnHandQty(),
                        option.getCategoryLevel()
                ))
                .toList();
    }

    public InventoryOptionResponse constantOption(String code, String name) {
        return new InventoryOptionResponse(code, name, null, null, null, null, null, null, null, null);
    }

    private static void addCategoryPathItem(
            List<CategoryPathItemResponse> path,
            Long id,
            String name,
            Integer level
    ) {
        if (id == null || name == null || name.isBlank() || path.stream().anyMatch(item -> id.equals(item.id()))) {
            return;
        }
        path.add(new CategoryPathItemResponse(id, name, level));
    }

    private static LocalDate toLocalDate(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException exception) {
            log.error(
                    "Failed to parse inventory JSON aggregation: type={}, payloadLength={}",
                    type.getType(),
                    json.length(),
                    exception
            );
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
