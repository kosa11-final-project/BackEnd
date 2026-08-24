package com.stockit.backend.feature.inventorysync.vo;

/** 동기화 실행에 연결된 조회용 스냅샷의 DB 저장 상태입니다. */
public class InventorySyncSnapshotStateVO {
    private Long inventorySyncRunId;
    private int dashboardReady;
    private int inventoryStatisticsReady;
    private String dashboardStatus;
    private String inventoryStatisticsStatus;

    public Long getInventorySyncRunId() {
        return inventorySyncRunId;
    }

    public void setInventorySyncRunId(Long inventorySyncRunId) {
        this.inventorySyncRunId = inventorySyncRunId;
    }

    public boolean isDashboardReady() {
        return dashboardReady == 1;
    }

    public void setDashboardReady(int dashboardReady) {
        this.dashboardReady = dashboardReady;
    }

    public boolean isInventoryStatisticsReady() {
        return inventoryStatisticsReady == 1;
    }

    public void setInventoryStatisticsReady(int inventoryStatisticsReady) {
        this.inventoryStatisticsReady = inventoryStatisticsReady;
    }

    public String getDashboardStatus() {
        return dashboardStatus;
    }

    public void setDashboardStatus(String dashboardStatus) {
        this.dashboardStatus = dashboardStatus;
    }

    public String getInventoryStatisticsStatus() {
        return inventoryStatisticsStatus;
    }

    public void setInventoryStatisticsStatus(String inventoryStatisticsStatus) {
        this.inventoryStatisticsStatus = inventoryStatisticsStatus;
    }
}
