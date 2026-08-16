package com.stockit.backend.feature.inventory.vo;

import java.math.BigDecimal;
import java.sql.Date;

public class InventoryLotVO {

    private Long lotId;
    private String lotNumber;
    private String lotStatus;
    private BigDecimal quantity;
    private BigDecimal availableQuantity;
    private BigDecimal reservedQuantity;
    private Date manufacturedDate;
    private Date receivedDate;
    private Date expiryDate;
    private Date saleStopDate;
    private Integer expiryDays;
    private Integer fefoPriority;
    private String warehouseCode;
    private String warehouseName;

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }
    public String getLotStatus() { return lotStatus; }
    public void setLotStatus(String lotStatus) { this.lotStatus = lotStatus; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal availableQuantity) { this.availableQuantity = availableQuantity; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Date getManufacturedDate() { return manufacturedDate; }
    public void setManufacturedDate(Date manufacturedDate) { this.manufacturedDate = manufacturedDate; }
    public Date getReceivedDate() { return receivedDate; }
    public void setReceivedDate(Date receivedDate) { this.receivedDate = receivedDate; }
    public Date getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }
    public Date getSaleStopDate() { return saleStopDate; }
    public void setSaleStopDate(Date saleStopDate) { this.saleStopDate = saleStopDate; }
    public Integer getExpiryDays() { return expiryDays; }
    public void setExpiryDays(Integer expiryDays) { this.expiryDays = expiryDays; }
    public Integer getFefoPriority() { return fefoPriority; }
    public void setFefoPriority(Integer fefoPriority) { this.fefoPriority = fefoPriority; }
    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
}
