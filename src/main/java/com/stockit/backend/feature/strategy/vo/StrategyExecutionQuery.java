package com.stockit.backend.feature.strategy.vo;

public record StrategyExecutionQuery(
        int page,
        int size,
        String query,
        String caseStatus,
        String actionType,
        String sortDirection,
        long offset
) {
    public StrategyExecutionQuery(
            int page,
            int size,
            String query,
            String caseStatus,
            String actionType,
            String sortDirection
    ) {
        this(page, size, query, caseStatus, actionType, sortDirection, (long) page * size);
    }

    public long getOffset() {
        return offset;
    }

    public int getSize() {
        return size;
    }

    public String getQuery() {
        return query;
    }

    public String getCaseStatus() {
        return caseStatus;
    }

    public String getActionType() {
        return actionType;
    }

    public String getSortDirection() {
        return sortDirection;
    }
}
