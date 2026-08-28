package com.stockit.backend.feature.inventory.service;

import java.io.IOException;
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
import com.stockit.backend.feature.inventory.dto.response.SkuChannelPriceResponse;
import com.stockit.backend.feature.inventory.dto.response.UnassignedInventoryResponse;
import com.stockit.backend.feature.inventory.vo.InventoryItemVO;
import com.stockit.backend.feature.inventory.vo.InventoryLotVO;
import com.stockit.backend.feature.inventory.vo.InventoryOptionVO;
import com.stockit.backend.feature.inventory.vo.SkuChannelPriceVO;

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
        List<SalesPointResponse> rawSalesPoints = readJson(item.getSalesPointsJson(), SALES_POINTS_TYPE);
        UnassignedInventoryResponse unassignedInventory = toUnassignedInventory(item, locations, rawSalesPoints);
        List<SalesPointResponse> salesPoints = namedSalesPoints(rawSalesPoints);
        String rowId = item.getSkuCode();

        return new InventoryItemResponse(
                rowId,
                item.getSkuId(),
                item.getProductCode(),
                item.getProductName(),
                item.getSupplierName(),
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
                item.getShortageYn(),
                new RiskResponse(
                        item.getAssessmentStatus() == null ? "UNASSESSED" : item.getAssessmentStatus(),
                        item.getRiskGrade(),
                        item.getRiskReason()
                ),
                locations,
                item.getLocationCount() == null ? locations.size() : item.getLocationCount(),
                salesPoints,
                item.getOwnerSalesPointCount() == null ? salesPoints.size() : item.getOwnerSalesPointCount(),
                unassignedInventory,
                item.getLotCount(),
                item.getNearestExpiryDays(),
                item.getNearestExpiryDate() == null ? null : item.getNearestExpiryDate().toLocalDate(),
                item.getUpdatedAt() == null ? null : item.getUpdatedAt().toInstant(),
                item.getExpectedDisposalQty()
        );
    }

    public InventoryDetailResponse toDetailResponse(InventoryItemVO item, List<InventoryLotResponse> lots) {
        return toDetailResponse(item, lots, List.of());
    }

    public InventoryDetailResponse toDetailResponse(
            InventoryItemVO item,
            List<InventoryLotResponse> lots,
            List<SkuChannelPriceResponse> channelPrices
    ) {
        List<LocationResponse> locations = readJson(item.getLocationsJson(), LOCATIONS_TYPE);
        List<SalesPointResponse> rawSalesPoints = readJson(item.getSalesPointsJson(), SALES_POINTS_TYPE);
        UnassignedInventoryResponse unassignedInventory = toUnassignedInventory(item, locations, rawSalesPoints);
        List<SalesPointResponse> salesPoints = namedSalesPoints(rawSalesPoints);
        String rowId = item.getSkuCode() + ":" + item.getSalesPointCode();

        return new InventoryDetailResponse(
                rowId,
                item.getSkuId(),
                item.getProductCode(),
                item.getProductName(),
                item.getSupplierName(),
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
                unassignedInventory,
                item.getLotCount(),
                item.getNearestExpiryDays(),
                item.getNearestExpiryDate() == null ? null : item.getNearestExpiryDate().toLocalDate(),
                item.getUpdatedAt() == null ? null : item.getUpdatedAt().toInstant(),
                lots,
                channelPrices,
                item.getExpectedDisposalQty()
        );
    }

    public SkuChannelPriceResponse toSkuChannelPriceResponse(SkuChannelPriceVO vo) {
        if (vo == null) return null;
        return new SkuChannelPriceResponse(
                vo.getSalesPointCode(),
                vo.getSalesPointName(),
                vo.getSellingPrice(),
                vo.getActualPrice(),
                vo.getMinimumSellingPrice(),
                vo.getEffectiveFrom() != null ? vo.getEffectiveFrom().toLocalDate() : null,
                vo.getEffectiveTo() != null ? vo.getEffectiveTo().toLocalDate() : null,
                vo.getPriceStatus()
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
                lot.getManufacturedDate() == null ? null : lot.getManufacturedDate().toLocalDate(),
                lot.getReceivedDate() == null ? null : lot.getReceivedDate().toLocalDate(),
                lot.getExpiryDate() == null ? null : lot.getExpiryDate().toLocalDate(),
                lot.getSaleStopDate() == null ? null : lot.getSaleStopDate().toLocalDate(),
                lot.getExpiryDays(),
                lot.getFefoPriority(),
                lot.getWarehouseCode(),
                lot.getWarehouseName()
        );
    }

    public List<InventoryOptionResponse> mapOptions(List<InventoryOptionVO> options) {
        if (options == null) return List.of();
        return options.stream()
                .map(this::toOptionResponse)
                .toList();
    }

    public InventoryOptionResponse constantOption(String value, String label) {
        return new InventoryOptionResponse(value, label, null, null, null, "ACTIVE", null, null, null, null);
    }

    private InventoryOptionResponse toOptionResponse(InventoryOptionVO option) {
        return new InventoryOptionResponse(
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
        );
    }

    private void addCategoryPathItem(List<CategoryPathItemResponse> path, Long id, String name, Integer level) {
        if (id != null && name != null && !name.isBlank()
                && path.stream().noneMatch(item -> id.equals(item.id()))) {
            path.add(new CategoryPathItemResponse(id, name, level));
        }
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (IOException e) {
            log.error("inventory aggregation JSON parsing failed: type={}, payloadLength={}",
                    typeRef.getType(), json.length(), e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private List<SalesPointResponse> namedSalesPoints(List<SalesPointResponse> rawSalesPoints) {
        return rawSalesPoints.stream()
                .filter(point -> !isUnassigned(point))
                .map(this::withoutWarehouse)
                .toList();
    }

    private UnassignedInventoryResponse toUnassignedInventory(
            InventoryItemVO item,
            List<LocationResponse> locations,
            List<SalesPointResponse> rawSalesPoints
    ) {
        List<LocationResponse> unassignedLocations = item.getUnassignedLocationsJson() == null
                ? locations
                : readJson(item.getUnassignedLocationsJson(), LOCATIONS_TYPE);
        SalesPointResponse center = rawSalesPoints.stream()
                .filter(this::isUnassigned)
                .findFirst()
                .orElse(null);

        return new UnassignedInventoryResponse(
                firstNonNull(item.getUnassignedCurrentQty(), center == null ? null : center.currentQuantity()),
                firstNonNull(item.getUnassignedAvailableQty(), center == null ? null : center.availableQuantity()),
                firstNonNull(item.getUnassignedReservedQty(), center == null ? null : center.reservedQuantity()),
                firstNonNull(item.getUnassignedInventoryFactState(), center == null ? null : center.salesPointState()),
                firstNonNull(item.getUnassignedShortageYn(), center == null ? null : center.shortageYn()),
                firstNonNull(item.getUnassignedRiskGrade(), center == null ? null : center.riskGrade()),
                firstNonNull(item.getUnassignedAssessmentStatus(), center == null ? null : center.assessmentStatus()),
                item.getUnassignedRiskReason(),
                unassignedLocations,
                item.getUnassignedLocationCount() == null ? unassignedLocations.size() : item.getUnassignedLocationCount()
        );
    }

    private SalesPointResponse withoutWarehouse(SalesPointResponse point) {
        return new SalesPointResponse(
                point.salesPointId(),
                point.salesPointCode(),
                point.salesPointName(),
                point.channelType(),
                point.currentQuantity(),
                point.availableQuantity(),
                point.reservedQuantity(),
                point.riskGrade(),
                point.assessmentStatus(),
                point.shortageYn(),
                null,
                point.sellingPrice(),
                point.salesPointState(),
                point.priceStatus()
        );
    }

    private boolean isUnassigned(SalesPointResponse point) {
        return point != null
                && ("UNASSIGNED".equalsIgnoreCase(point.salesPointCode())
                || "CENTER".equalsIgnoreCase(point.channelType())
                || "CENTER_ONLY".equalsIgnoreCase(point.salesPointState()));
    }

    private <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }
}
