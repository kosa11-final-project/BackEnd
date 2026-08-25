package com.stockit.backend.feature.inventorysync.adapter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 원천 adapter가 반환하는 메모리 전용 canonical typed record입니다.
 * 정제 중간 결과를 DB staging table에 복제하지 않고 job 범위에서만 보관합니다.
 */
public record CanonicalInventoryRecord(
        String sourceType,
        String sourceRecordKey,
        Long productId,
        Long categoryId,
        Long skuId,
        Long salesPointId,
        Long warehouseId,
        Long lotId,
        Long inventoryBalanceId,
        Long skuChannelPriceId,
        Long skuCostId,
        Long inventoryPolicyId,
        String productName,
        String brandName,
        String productStatus,
        String saleAvailableYn,
        String skuName,
        BigDecimal netWeight,
        String weightUnit,
        BigDecimal packageQuantity,
        String unitCode,
        String storageType,
        BigDecimal sellingPrice,
        BigDecimal actualPrice,
        BigDecimal productCost,
        BigDecimal paymentFee,
        BigDecimal logisticsCost,
        LocalDate priceEffectiveFrom,
        LocalDate priceEffectiveTo,
        BigDecimal skuUnitCost,
        LocalDate manufacturedDate,
        LocalDate receivedDate,
        LocalDate expiryDate,
        LocalDate saleStopDate,
        String lotStatus,
        BigDecimal safetyStockQty,
        BigDecimal targetStockQty,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        String recordHash,
        long sourceVersion,
        long rowVersion
) {

    public CanonicalInventoryRecord {
        sourceType = requireText(sourceType, "sourceType");
        sourceRecordKey = requireText(sourceRecordKey, "sourceRecordKey");
        recordHash = requireText(recordHash, "recordHash");
        if (inventoryBalanceId == null || inventoryBalanceId <= 0) {
            throw new IllegalArgumentException("inventoryBalanceId must be positive");
        }
        if (skuId == null || skuId <= 0) {
            throw new IllegalArgumentException("skuId must be positive");
        }
        if (warehouseId == null || warehouseId <= 0) {
            throw new IllegalArgumentException("warehouseId must be positive");
        }
        if (productId == null || productId <= 0 || categoryId == null || categoryId <= 0 || lotId == null || lotId <= 0) {
            throw new IllegalArgumentException("productId, categoryId and lotId must be positive");
        }
        productName = requireText(productName, "productName");
        productStatus = requireText(productStatus, "productStatus");
        skuName = requireText(skuName, "skuName");
        unitCode = requireText(unitCode, "unitCode");
        lotStatus = requireText(lotStatus, "lotStatus");
        if (!"WAREHOUSE".equals(sourceType)) {
            saleAvailableYn = requireFlag(saleAvailableYn, "saleAvailableYn");
        }
        packageQuantity = nonNegative(packageQuantity, "packageQuantity");
        netWeight = optionalNonNegative(netWeight, "netWeight");
        if (skuChannelPriceId != null) {
            sellingPrice = nonNegative(sellingPrice, "sellingPrice");
            actualPrice = nonNegative(actualPrice, "actualPrice");
            productCost = nonNegative(productCost, "productCost");
            paymentFee = optionalNonNegative(paymentFee, "paymentFee");
            logisticsCost = optionalNonNegative(logisticsCost, "logisticsCost");
            if (priceEffectiveFrom == null) {
                throw new IllegalArgumentException("priceEffectiveFrom is required for a mapped price");
            }
        }
        if (skuCostId != null) {
            skuUnitCost = nonNegative(skuUnitCost, "skuUnitCost");
        }
        if (inventoryPolicyId != null) {
            safetyStockQty = nonNegative(safetyStockQty, "safetyStockQty");
            targetStockQty = optionalNonNegative(targetStockQty, "targetStockQty");
        }
        if (sourceVersion <= 0 || rowVersion <= 0) {
            throw new IllegalArgumentException("sourceVersion and rowVersion must be positive");
        }
        onHandQty = nonNegative(onHandQty, "onHandQty");
        reservedQty = nonNegative(reservedQty, "reservedQty");
        if (reservedQty.compareTo(onHandQty) > 0) {
            throw new IllegalArgumentException("reservedQty must not exceed onHandQty");
        }
    }

    /** 수량·capacity 단위 테스트에서 사용하는 최소 canonical fixture 생성자입니다. */
    public CanonicalInventoryRecord(
            String sourceType, String sourceRecordKey, Long productId, Long skuId,
            Long salesPointId, Long warehouseId, Long lotId, Long inventoryBalanceId,
            Long skuChannelPriceId, Long skuCostId, Long inventoryPolicyId,
            BigDecimal onHandQty, BigDecimal reservedQty, String recordHash,
            long sourceVersion, long rowVersion
    ) {
        this(sourceType, sourceRecordKey, productId, 1L, skuId, salesPointId, warehouseId, lotId,
                inventoryBalanceId, skuChannelPriceId, skuCostId, inventoryPolicyId,
                "PRODUCT-" + productId, null, "ACTIVE", "WAREHOUSE".equals(sourceType) ? null : "Y",
                "SKU-" + skuId, null, null, BigDecimal.ONE, "EA", null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, "AVAILABLE", null, null,
                onHandQty, reservedQty, recordHash, sourceVersion, rowVersion);
    }

    public String targetKey() {
        return inventoryBalanceId.toString();
    }

    public String riskScopeKey() {
        return skuId + ":" + Objects.toString(salesPointId, "UNASSIGNED");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireFlag(String value, String field) {
        String flag = requireText(value, field).toUpperCase();
        if (!flag.equals("Y") && !flag.equals("N")) {
            throw new IllegalArgumentException(field + " must be Y or N");
        }
        return flag;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }

    private static BigDecimal optionalNonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }
}
