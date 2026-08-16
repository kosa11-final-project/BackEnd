package com.stockit.backend.feature.inventory.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventory.dto.response.InventoryDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryFilterOptionsResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryItemResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryListResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotsResponse;
import com.stockit.backend.feature.inventory.dto.response.InventorySummaryResponse;
import com.stockit.backend.feature.inventory.mapper.InventoryMapper;
import com.stockit.backend.feature.inventory.service.InventoryQueryService;
import com.stockit.backend.feature.inventory.service.InventoryResponseMapper;
import com.stockit.backend.feature.inventory.vo.InventoryItemVO;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;
import com.stockit.backend.feature.inventory.vo.InventorySummaryVO;

@Service
@Transactional(readOnly = true)
public class InventoryQueryServiceImpl implements InventoryQueryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> RISK_GRADES = List.of("SAFE", "NORMAL", "CAUTION", "DANGER");
    private static final List<String> ASSESSMENT_STATUSES = List.of(
            "ASSESSED", "UNASSESSED", "STALE", "FAILED", "REASSESSING"
    );

    private static final Map<String, String> RISK_GRADE_NAMES = Map.of(
            "SAFE", "양호",
            "NORMAL", "보통",
            "CAUTION", "주의",
            "DANGER", "위험"
    );
    private static final Map<String, String> ASSESSMENT_STATUS_NAMES = Map.of(
            "ASSESSED", "판정 완료",
            "UNASSESSED", "미판정",
            "STALE", "만료됨",
            "FAILED", "판정 실패",
            "REASSESSING", "재판정 중"
    );

    private final InventoryMapper inventoryMapper;
    private final InventoryResponseMapper responseMapper;

    public InventoryQueryServiceImpl(InventoryMapper inventoryMapper, InventoryResponseMapper responseMapper) {
        this.inventoryMapper = inventoryMapper;
        this.responseMapper = responseMapper;
    }

    @Override
    public InventoryListResponse find(InventoryQuery query) {
        long totalCount = inventoryMapper.countInventory(query);
        List<InventoryItemResponse> items = inventoryMapper.selectInventoryList(query).stream()
                .map(responseMapper::toItemResponse)
                .toList();
        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / query.size());

        return new InventoryListResponse(
                items,
                totalCount,
                query.page(),
                query.size(),
                totalPages,
                items.isEmpty() && hasFilter(query)
        );
    }

    @Override
    public InventorySummaryResponse summary(InventoryQuery query) {
        InventorySummaryVO summary = inventoryMapper.selectInventorySummary(query);
        if (summary == null) {
            return new InventorySummaryResponse(null, null, null, 0, 0, 0, 0, null);
        }
        return new InventorySummaryResponse(
                summary.getTotalCurrentQty(),
                summary.getTotalAvailableQty(),
                summary.getTotalReservedQty(),
                summary.getUnderSafetyCount(),
                summary.getDangerRiskCount(),
                summary.getCautionRiskCount(),
                summary.getSafeRiskCount(),
                summary.getLastSyncTime() == null ? null : summary.getLastSyncTime().toInstant()
        );
    }

    @Override
    public InventoryFilterOptionsResponse filterOptions() {
        return new InventoryFilterOptionsResponse(
                responseMapper.mapOptions(inventoryMapper.selectChannelOptions()),
                responseMapper.mapOptions(inventoryMapper.selectSalesPointOptions()),
                responseMapper.mapOptions(inventoryMapper.selectWarehouseOptions()),
                responseMapper.mapOptions(inventoryMapper.selectRegionOptions()),
                responseMapper.mapOptions(inventoryMapper.selectCategoryOptions()),
                responseMapper.mapOptions(inventoryMapper.selectStorageTypeOptions()),
                RISK_GRADES.stream()
                        .map(value -> responseMapper.constantOption(value, RISK_GRADE_NAMES.getOrDefault(value, value)))
                        .toList(),
                ASSESSMENT_STATUSES.stream()
                        .map(value -> responseMapper.constantOption(value, ASSESSMENT_STATUS_NAMES.getOrDefault(value, value)))
                        .toList()
        );
    }

    @Override
    public InventoryDetailResponse detail(String skuCode, String salesPointCode) {
        String normalizedSkuCode = requiredCode(skuCode, "skuCode");
        String normalizedSalesPointCode = requiredCode(salesPointCode, "salesPointCode");
        InventoryItemVO item = inventoryMapper.selectInventoryDetail(
                normalizedSkuCode,
                normalizedSalesPointCode,
                LocalDate.now(BUSINESS_ZONE)
        );
        if (item == null) {
            throw new AppException(ErrorCode.NOT_FOUND);
        }

        return responseMapper.toDetailResponse(item, List.of());
    }

    @Override
    public InventoryLotsResponse lots(String skuCode, String salesPointCode) {
        String normalizedSkuCode = requiredCode(skuCode, "skuCode");
        String normalizedSalesPointCode = requiredCode(salesPointCode, "salesPointCode");
        LocalDate asOfDate = LocalDate.now(BUSINESS_ZONE);
        InventoryItemVO item = inventoryMapper.selectInventoryDetail(
                normalizedSkuCode,
                normalizedSalesPointCode,
                asOfDate
        );
        if (item == null) {
            throw new AppException(ErrorCode.NOT_FOUND);
        }

        List<InventoryLotResponse> items = inventoryMapper.selectInventoryLots(
                normalizedSkuCode,
                normalizedSalesPointCode,
                asOfDate
        ).stream()
                .map(responseMapper::toLotResponse)
                .toList();

        return new InventoryLotsResponse(items, items.size());
    }

    private static String requiredCode(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        return value.trim();
    }

    private static boolean hasFilter(InventoryQuery query) {
        return query.q() != null
                || !query.channelTypes().isEmpty()
                || !query.salesPointCodes().isEmpty()
                || !query.warehouseCodes().isEmpty()
                || !query.regionCodes().isEmpty()
                || query.categoryId() != null
                || !query.storageTypes().isEmpty()
                || !query.riskGrades().isEmpty()
                || !query.assessmentStatuses().isEmpty();
    }
}
