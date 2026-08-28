package com.stockit.backend.feature.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
        item.setSkuId(1001L);
        item.setExpectedDisposalQty(new BigDecimal("4"));
        item.setSalesPointsJson("[{\"salesPointId\":77,\"salesPointCode\":\"GREETING\",\"salesPointName\":\"그리팅\",\"channelType\":\"GREETING\"}]");
        item.setSupplierName("테스트 공급사");
        item.setAssessmentStatus("ASSESSED");
        item.setRiskGrade("WARNING");
        item.setRiskReason("[ASSESSED/v1.1.0/SHORTAGE_D30] D+30 부족 예상 | 산식: 가용재고=100");
        given(inventoryMapper.selectInventoryDetail(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(item);

        InventoryDetailResponse response = inventoryQueryService.detail("SKU-1", "GREETING");

        assertThat(response.rowId()).isEqualTo("SKU-1:GREETING");
        assertThat(response.skuId()).isEqualTo(1001L);
        assertThat(response.skuCode()).isEqualTo("SKU-1");
        assertThat(response.supplierName()).isEqualTo("테스트 공급사");
        assertThat(response.salesPointCode()).isEqualTo("GREETING");
        assertThat(response.salesPoints()).singleElement()
                .extracting(point -> point.salesPointId())
                .isEqualTo(77L);
        assertThat(response.category()).isNotNull();
        assertThat(response.category().path()).extracting(path -> path.name())
                .containsExactly("식품", "베이커리/간식", "베이커리");
        assertThat(response.risk().reason()).contains("SHORTAGE_D30", "산식: 가용재고=100");
        assertThat(response.lots()).isEmpty();
        assertThat(new ObjectMapper().valueToTree(response).path("expectedDisposalQuantity").asText())
                .isEqualTo("4");
    }

    @Test
    void unassignedInventoryDoesNotCopySalesPointPrices() {
        InventoryItemVO item = createItemVO("SKU-1", "UNASSIGNED");
        item.setSalesPointName("판매처 미할당");
        item.setChannelType("CENTER");
        given(inventoryMapper.selectInventoryDetail(eq("SKU-1"), eq("UNASSIGNED"), any(LocalDate.class)))
                .willReturn(item);

        InventoryDetailResponse response = inventoryQueryService.detail("SKU-1", "UNASSIGNED");

        assertThat(response.channelPrices()).isEmpty();
        verify(inventoryMapper, never()).selectSkuChannelPrices(eq("SKU-1"), any(LocalDate.class));
    }

    @Test
    void detailCarriesCenterOnlyInventorySeparatelyFromSelectedSeller() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        item.setUnassignedCurrentQty(new BigDecimal("12"));
        item.setUnassignedAvailableQty(new BigDecimal("10"));
        item.setUnassignedReservedQty(new BigDecimal("2"));
        item.setUnassignedInventoryFactState("AVAILABLE");
        item.setUnassignedShortageYn("Y");
        item.setUnassignedRiskGrade("WARNING");
        item.setUnassignedAssessmentStatus("ASSESSED");
        item.setUnassignedRiskReason("미할당 공용재고 예측 데이터 없음");
        item.setUnassignedLocationsJson("[{\"warehouseCode\":\"DC-A\",\"warehouseName\":\"센터 A\",\"quantity\":12}]");
        item.setUnassignedLocationCount(1);
        item.setSalesPointsJson("[{\"salesPointCode\":\"GREETING\",\"salesPointName\":\"그리팅\",\"channelType\":\"GREETING\",\"warehouseName\":\"센터 A\"}]");
        given(inventoryMapper.selectInventoryDetail(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(item);

        InventoryDetailResponse response = inventoryQueryService.detail("SKU-1", "GREETING");

        assertThat(response.salesPoints()).singleElement().satisfies(point -> {
            assertThat(point.salesPointCode()).isEqualTo("GREETING");
            assertThat(point.warehouseName()).isNull();
        });
        assertThat(response.unassignedInventory().currentQuantity()).isEqualByComparingTo("12");
        assertThat(response.unassignedInventory().shortageYn()).isEqualTo("Y");
        assertThat(response.unassignedInventory().riskGrade()).isEqualTo("WARNING");
        assertThat(response.unassignedInventory().assessmentStatus()).isEqualTo("ASSESSED");
        assertThat(response.unassignedInventory().riskReason()).contains("예측 데이터 없음");
        assertThat(response.unassignedInventory().locations()).extracting(location -> location.warehouseCode())
                .containsExactly("DC-A");
    }

    @Test
    void findReturnsSkuRowIdAndServerPaginationMetadata() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        item.setTotalCount(5L);
        item.setExpectedDisposalQty(new BigDecimal("7"));
        item.setSalesPointsJson("[{\"salesPointCode\":\"GREETING\",\"salesPointName\":\"그리팅\",\"channelType\":\"GREETING\",\"currentQuantity\":50,\"availableQuantity\":40,\"reservedQuantity\":10,\"riskGrade\":\"GOOD\",\"warehouseName\":\"경인 1센터\"}]");
        var query = new InventoryQueryRequest();
        query.setPage(2);
        query.setSize(2);

        given(inventoryMapper.selectInventoryList(any())).willReturn(List.of(item));

        InventoryListResponse response = inventoryQueryService.find(query.toQuery(LocalDate.of(2026, 8, 14)));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).rowId()).isEqualTo("SKU-1");
        assertThat(response.items().get(0).salesPoints()).extracting(point -> point.salesPointCode())
                .containsExactly("GREETING");
        assertThat(response.items().get(0).expectedDisposalQuantity()).isEqualByComparingTo("7");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        verify(inventoryMapper, never()).countInventory(any());
    }

    @Test
    void findPreservesEachSalesPointAssessmentStatusFromThePersistedRiskSnapshot() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        item.setTotalCount(1L);
        item.setSalesPointsJson("[{\"salesPointCode\":\"GREETING\",\"salesPointName\":\"그리팅\","
                + "\"channelType\":\"GREETING\",\"currentQuantity\":50,\"availableQuantity\":40,"
                + "\"reservedQuantity\":10,\"riskGrade\":\"NORMAL\",\"assessmentStatus\":\"ASSESSED\"}]");
        given(inventoryMapper.selectInventoryList(any())).willReturn(List.of(item));

        InventoryListResponse response = inventoryQueryService.find(
                new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 28))
        );

        assertThat(response.items()).singleElement().satisfies(mapped ->
                assertThat(mapped.salesPoints()).singleElement().satisfies(point -> {
                    assertThat(point.riskGrade()).isEqualTo("NORMAL");
                    assertThat(new ObjectMapper().valueToTree(point).path("assessmentStatus").asText())
                            .isEqualTo("ASSESSED");
                }));
    }

    @Test
    void findSeparatesCenterOnlyInventoryFromNamedSalesPoints() {
        InventoryItemVO item = createItemVO("SKU-1", "GREETING");
        item.setTotalCount(1L);
        item.setLocationsJson("[{\"warehouseCode\":\"DC-A\",\"warehouseName\":\"센터 A\",\"quantity\":40}]");
        item.setSalesPointsJson("["
                + "{\"salesPointCode\":\"GREETING\",\"salesPointName\":\"그리팅\",\"channelType\":\"GREETING\","
                + "\"currentQuantity\":60,\"availableQuantity\":55,\"reservedQuantity\":5,\"warehouseName\":\"센터 A\",\"salesPointState\":\"OWNED\"},"
                + "{\"salesPointCode\":\"UNASSIGNED\",\"salesPointName\":\"판매처 미할당\",\"channelType\":\"CENTER\","
                + "\"currentQuantity\":40,\"availableQuantity\":35,\"reservedQuantity\":5,\"salesPointState\":\"CENTER_ONLY\"}"
                + "]");

        given(inventoryMapper.selectInventoryList(any())).willReturn(List.of(item));

        var response = inventoryQueryService.find(new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 14)));

        assertThat(response.items()).singleElement().satisfies(mapped -> {
            assertThat(mapped.salesPoints()).extracting(point -> point.salesPointCode()).containsExactly("GREETING");
            assertThat(mapped.salesPoints().get(0).warehouseName()).isNull();
            assertThat(mapped.unassignedInventory().currentQuantity()).isEqualByComparingTo("40");
            assertThat(mapped.unassignedInventory().availableQuantity()).isEqualByComparingTo("35");
            assertThat(mapped.unassignedInventory().locations()).hasSize(1);
        });
        verify(inventoryMapper, never()).countInventory(any());
    }

    @Test
    void findDoesNotRunASeparateCountForAnEmptyFirstPage() {
        given(inventoryMapper.selectInventoryList(any())).willReturn(List.of());

        InventoryListResponse response = inventoryQueryService.find(
                new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 14))
        );

        assertThat(response.totalCount()).isZero();
        assertThat(response.totalPages()).isEqualTo(1);
        verify(inventoryMapper, never()).countInventory(any());
    }

    @Test
    void findFallsBackToCountOnlyForAnEmptyOutOfRangePage() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setPage(3);
        request.setSize(20);
        given(inventoryMapper.selectInventoryList(any())).willReturn(List.of());
        given(inventoryMapper.countInventory(any())).willReturn(25L);

        InventoryListResponse response = inventoryQueryService.find(
                request.toQuery(LocalDate.of(2026, 8, 14))
        );

        assertThat(response.totalCount()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(2);
        verify(inventoryMapper).countInventory(any());
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
    void filterOptionsOnlyExposeAssessmentStatusesBackedByInventoryRows() {
        var response = inventoryQueryService.filterOptions();

        assertThat(response.assessmentStatuses())
                .extracting(option -> option.code())
                .containsExactly("ASSESSED", "UNASSESSED");
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
        given(inventoryMapper.countInventoryScope("SKU-1", "GREETING")).willReturn(1);

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
        given(inventoryMapper.countInventoryScope("SKU-1", "GREETING")).willReturn(1);
        given(inventoryMapper.selectInventoryLots(eq("SKU-1"), eq("GREETING"), any(LocalDate.class)))
                .willReturn(List.of());

        InventoryLotsResponse response = inventoryQueryService.lots("SKU-1", "GREETING");

        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
        verify(inventoryMapper, never()).selectInventoryDetail(eq("SKU-1"), eq("GREETING"), any(LocalDate.class));
    }

    @Test
    void lotsReturnsLotItemsWhenItemExistsAndHasLots() {
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

        given(inventoryMapper.countInventoryScope("SKU-1", "GREETING")).willReturn(1);
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
        given(inventoryMapper.countInventoryScope("SKU-999", "GREETING")).willReturn(0);

        assertThatThrownBy(() -> inventoryQueryService.lots("SKU-999", "GREETING"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
        verify(inventoryMapper, never()).selectInventoryLots(eq("SKU-999"), eq("GREETING"), any(LocalDate.class));
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
