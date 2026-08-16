package com.stockit.backend.feature.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.inventory.dto.response.InventoryDetailResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryListResponse;
import com.stockit.backend.feature.inventory.dto.response.InventoryLotsResponse;
import com.stockit.backend.feature.inventory.mapper.InventoryMapper;
import com.stockit.backend.feature.inventory.service.impl.InventoryQueryServiceImpl;
import com.stockit.backend.feature.inventory.vo.InventoryItemVO;
import com.stockit.backend.feature.inventory.vo.InventoryLotVO;
import com.stockit.backend.feature.inventory.vo.InventorySummaryVO;
import com.stockit.backend.feature.inventory.dto.request.InventoryQueryRequest;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceImplTest {

    @Mock
    private InventoryMapper inventoryMapper;

    private InventoryQueryService inventoryQueryService;

    @BeforeEach
    void setUp() {
        InventoryResponseMapper responseMapper = new InventoryResponseMapper(new ObjectMapper());
        inventoryQueryService = new InventoryQueryServiceImpl(inventoryMapper, responseMapper);
    }


    @Test
    void detailReturnsHeaderWithoutAssemblingLots() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        given(inventoryMapper.selectInventoryDetail(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(item);

        InventoryDetailResponse response = inventoryQueryService.detail("SKU-1", "GREETING");

        assertThat(response.rowId()).isEqualTo("SKU-1:GREETING");
        assertThat(response.skuCode()).isEqualTo("SKU-1");
        assertThat(response.salesPointCode()).isEqualTo("GREETING");
        assertThat(response.category()).isNotNull();
        assertThat(response.category().path()).extracting(path -> path.name())
                .containsExactly("식품", "베이커리/간식", "베이커리");
        assertThat(response.lots()).isEmpty();
    }

    @Test
    void findReturnsSkuRowIdAndServerPaginationMetadata() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        item.setSalesPointsJson("[{\"salesPointCode\":\"GREETING\",\"salesPointName\":\"그리팅\",\"channelType\":\"GREETING\",\"currentQuantity\":50,\"availableQuantity\":40,\"reservedQuantity\":10,\"riskGrade\":\"SAFE\",\"warehouseName\":\"경인 1센터\"}]");
        var query = new InventoryQueryRequest();
        query.setPage(2);
        query.setSize(2);

        given(inventoryMapper.countInventory(any())).willReturn(5L);
        given(inventoryMapper.selectInventoryList(any())).willReturn(List.of(item));

        InventoryListResponse response = inventoryQueryService.find(query.toQuery(LocalDate.of(2026, 8, 14)));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).rowId()).isEqualTo("SKU-1");
        assertThat(response.items().get(0).salesPoints()).extracting(point -> point.salesPointCode())
                .containsExactly("GREETING");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void preservesUnavailableQuantitiesInsteadOfConvertingThemToZero() {
        InventorySummaryVO summary = new InventorySummaryVO();
        given(inventoryMapper.selectInventorySummary(any())).willReturn(summary);

        var response = inventoryQueryService.summary(
                new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 14))
        );

        assertThat(response.totalCurrentQuantity()).isNull();
        assertThat(response.totalAvailableQuantity()).isNull();
        assertThat(response.totalReservedQuantity()).isNull();
    }

    @Test
    void preservesNullQuantitiesInDetailAndLots() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        item.setCurrentQty(null);
        item.setAvailableQty(null);
        item.setReservedQty(null);
        given(inventoryMapper.selectInventoryDetail(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(item);

        InventoryLotVO lot = new InventoryLotVO();
        lot.setLotId(10L);
        lot.setLotNumber("LOT-NULL");
        given(inventoryMapper.selectInventoryLots(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(List.of(lot));

        InventoryDetailResponse detail = inventoryQueryService.detail("SKU-1", "GREETING");
        InventoryLotsResponse lots = inventoryQueryService.lots("SKU-1", "GREETING");

        assertThat(detail.currentQuantity()).isNull();
        assertThat(detail.availableQuantity()).isNull();
        assertThat(detail.reservedQuantity()).isNull();
        assertThat(lots.items().get(0).quantity()).isNull();
        assertThat(lots.items().get(0).availableQuantity()).isNull();
        assertThat(lots.items().get(0).reservedQuantity()).isNull();
    }

    @Test
    void detailThrowsNotFoundWhenItemDoesNotExist() {
        given(inventoryMapper.selectInventoryDetail(eq("SKU-999"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(null);

        assertThatThrownBy(() -> inventoryQueryService.detail("SKU-999", "GREETING"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    void lotsReturnsEmptyListWhenItemExistsButHasNoLots() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        given(inventoryMapper.selectInventoryDetail(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(item);
        given(inventoryMapper.selectInventoryLots(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(List.of());

        InventoryLotsResponse response = inventoryQueryService.lots("SKU-1", "GREETING");

        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void lotsReturnsLotItemsWhenItemExistsAndHasLots() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        InventoryLotVO lot = new InventoryLotVO();
        lot.setLotId(10L);
        lot.setLotNumber("LOT-2026-001");
        lot.setLotStatus("AVAILABLE");
        lot.setQuantity(new BigDecimal("100"));
        lot.setAvailableQuantity(new BigDecimal("80"));
        lot.setReservedQuantity(new BigDecimal("20"));
        lot.setFefoPriority(1);
        lot.setWarehouseCode("GYEONGIN_1");
        lot.setWarehouseName("경인 1센터");

        given(inventoryMapper.selectInventoryDetail(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(item);
        given(inventoryMapper.selectInventoryLots(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(List.of(lot));

        InventoryLotsResponse response = inventoryQueryService.lots("SKU-1", "GREETING");

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).lotNumber()).isEqualTo("LOT-2026-001");
        assertThat(response.items().get(0).fefoPriority()).isEqualTo(1);
    }

    @Test
    void lotsThrowsNotFoundWhenItemDoesNotExist() {
        given(inventoryMapper.selectInventoryDetail(eq("SKU-999"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(null);

        assertThatThrownBy(() -> inventoryQueryService.lots("SKU-999", "GREETING"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    private InventoryItemVO createItemVO(String skuCode, String salesPointCode) {
        InventoryItemVO item = new InventoryItemVO();
        item.setProductCode("P-1");
        item.setProductName("테스트 상품");
        item.setSkuCode(skuCode);
        item.setSkuName("테스트 SKU");
        item.setCategoryId(301L);
        item.setCategoryName("베이커리");
        item.setCategoryLevel(3);
        item.setParentCategoryId(20L);
        item.setParentCategoryName("베이커리/간식");
        item.setParentCategoryLevel(2);
        item.setGrandparentCategoryId(1L);
        item.setGrandparentCategoryName("식품");
        item.setGrandparentCategoryLevel(1);
        item.setChannelType("GREETING");
        item.setSalesPointCode(salesPointCode);
        item.setSalesPointName("그리팅 스토어");
        item.setStorageType("FROZEN");
        item.setCurrentQty(new BigDecimal("50"));
        item.setAvailableQty(new BigDecimal("40"));
        item.setReservedQty(new BigDecimal("10"));
        item.setInventoryFactState("AVAILABLE");
        item.setLocationsJson("[]");
        item.setSalesPointsJson("[]");
        return item;
    }
}
