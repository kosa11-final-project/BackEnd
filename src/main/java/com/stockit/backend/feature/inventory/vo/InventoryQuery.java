package com.stockit.backend.feature.inventory.vo;

import java.time.LocalDate;
import java.util.List;

public final class InventoryQuery {

    private final String q;
    private final List<String> channelTypes;
    private final List<String> salesPointCodes;
    private final List<String> warehouseCodes;
    private final List<String> regionCodes;
    private final Long categoryId;
    private final List<String> storageTypes;
    private final List<String> riskGrades;
    private final List<String> assessmentStatuses;
    private final int page;
    private final int size;
    private final String sortColumn;
    private final String sortDirection;
    private final LocalDate asOfDate;

    public InventoryQuery(
            String q,
            List<String> channelTypes,
            List<String> salesPointCodes,
            List<String> warehouseCodes,
            List<String> regionCodes,
            Long categoryId,
            List<String> storageTypes,
            List<String> riskGrades,
            List<String> assessmentStatuses,
            int page,
            int size,
            String sortColumn,
            String sortDirection,
            LocalDate asOfDate
    ) {
        this.q = q;
        this.channelTypes = List.copyOf(channelTypes == null ? List.of() : channelTypes);
        this.salesPointCodes = List.copyOf(salesPointCodes == null ? List.of() : salesPointCodes);
        this.warehouseCodes = List.copyOf(warehouseCodes == null ? List.of() : warehouseCodes);
        this.regionCodes = List.copyOf(regionCodes == null ? List.of() : regionCodes);
        this.categoryId = categoryId;
        this.storageTypes = List.copyOf(storageTypes == null ? List.of() : storageTypes);
        this.riskGrades = List.copyOf(riskGrades == null ? List.of() : riskGrades);
        this.assessmentStatuses = List.copyOf(assessmentStatuses == null ? List.of() : assessmentStatuses);
        this.page = page;
        this.size = size;
        this.sortColumn = sortColumn;
        this.sortDirection = sortDirection;
        this.asOfDate = asOfDate;
    }

    public String q() { return q; }
    public List<String> channelTypes() { return channelTypes; }
    public List<String> salesPointCodes() { return salesPointCodes; }
    public List<String> warehouseCodes() { return warehouseCodes; }
    public List<String> regionCodes() { return regionCodes; }
    public Long categoryId() { return categoryId; }
    public List<String> storageTypes() { return storageTypes; }
    public List<String> riskGrades() { return riskGrades; }
    public List<String> assessmentStatuses() { return assessmentStatuses; }
    public int page() { return page; }
    public int size() { return size; }
    public String sortColumn() { return sortColumn; }
    public String sortDirection() { return sortDirection; }
    public LocalDate asOfDate() { return asOfDate; }

    public String getQ() { return q; }
    public List<String> getChannelTypes() { return channelTypes; }
    public List<String> getSalesPointCodes() { return salesPointCodes; }
    public List<String> getWarehouseCodes() { return warehouseCodes; }
    public List<String> getRegionCodes() { return regionCodes; }
    public Long getCategoryId() { return categoryId; }
    public List<String> getStorageTypes() { return storageTypes; }
    public List<String> getRiskGrades() { return riskGrades; }
    public List<String> getAssessmentStatuses() { return assessmentStatuses; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public String getSortColumn() { return sortColumn; }
    public String getSortDirection() { return sortDirection; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public long getOffset() { return offset(); }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
