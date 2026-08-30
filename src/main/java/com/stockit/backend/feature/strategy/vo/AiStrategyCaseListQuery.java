package com.stockit.backend.feature.strategy.vo;

import java.time.LocalDateTime;
import java.time.LocalDate;

public record AiStrategyCaseListQuery(
        int page,
        int size,
        String searchText,
        Long strategyCaseId,
        String caseStatus,
        LocalDateTime createdFrom,
        LocalDateTime createdToExclusive,
        String channelType,
        String warehouseCode,
        LocalDate strategyFrom,
        LocalDate strategyTo,
        String sortDirection,
        long offset
) {
    public AiStrategyCaseListQuery(
            int page,
            int size,
            String searchText,
            Long strategyCaseId,
            String caseStatus,
            LocalDateTime createdFrom,
            LocalDateTime createdToExclusive,
            String channelType,
            String warehouseCode,
            LocalDate strategyFrom,
            LocalDate strategyTo,
            String sortDirection
    ) {
        this(page, size, searchText, strategyCaseId, caseStatus, createdFrom,
                createdToExclusive, channelType, warehouseCode, strategyFrom, strategyTo,
                sortDirection, (long) page * size);
    }

    public AiStrategyCaseListQuery(
            int page,
            int size,
            String searchText,
            Long strategyCaseId,
            String caseStatus,
            LocalDateTime createdFrom,
            LocalDateTime createdToExclusive,
            String sortDirection
    ) {
        this(page, size, searchText, strategyCaseId, caseStatus, createdFrom,
                createdToExclusive, null, null, null, null, sortDirection);
    }

    public String getSearchText() { return searchText; }
    public Long getStrategyCaseId() { return strategyCaseId; }
    public String getCaseStatus() { return caseStatus; }
    public LocalDateTime getCreatedFrom() { return createdFrom; }
    public LocalDateTime getCreatedToExclusive() { return createdToExclusive; }
    public String getChannelType() { return channelType; }
    public String getWarehouseCode() { return warehouseCode; }
    public LocalDate getStrategyFrom() { return strategyFrom; }
    public LocalDate getStrategyTo() { return strategyTo; }
    public String getSortDirection() { return sortDirection; }
    public long getOffset() { return offset; }
    public int getSize() { return size; }
}
