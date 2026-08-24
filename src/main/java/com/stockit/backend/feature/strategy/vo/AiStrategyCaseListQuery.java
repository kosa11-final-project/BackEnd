package com.stockit.backend.feature.strategy.vo;

import java.time.LocalDateTime;

public record AiStrategyCaseListQuery(
        int page,
        int size,
        String searchText,
        Long strategyCaseId,
        String caseStatus,
        LocalDateTime createdFrom,
        LocalDateTime createdToExclusive,
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
            String sortDirection
    ) {
        this(page, size, searchText, strategyCaseId, caseStatus, createdFrom,
                createdToExclusive, sortDirection, (long) page * size);
    }

    public String getSearchText() { return searchText; }
    public Long getStrategyCaseId() { return strategyCaseId; }
    public String getCaseStatus() { return caseStatus; }
    public LocalDateTime getCreatedFrom() { return createdFrom; }
    public LocalDateTime getCreatedToExclusive() { return createdToExclusive; }
    public String getSortDirection() { return sortDirection; }
    public long getOffset() { return offset; }
    public int getSize() { return size; }
}
