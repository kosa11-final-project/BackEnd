package com.stockit.backend.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.feature.inventory.dto.request.InventoryQueryRequest;
import com.stockit.backend.feature.inventory.vo.InventoryQuery;

class InventoryQueryRequestTest {

    @Test
    void usesFrontendCompatibleDefaultsAndOneBasedPagination() {
        InventoryQuery query = new InventoryQueryRequest().toQuery(LocalDate.of(2026, 8, 14));

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.sortColumn()).isEqualTo("updated_at");
        assertThat(query.sortDirection()).isEqualTo("DESC");
        assertThat(query.filterOperator()).isEqualTo("AND");
        assertThat(query.asOfDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void normalizesRepeatedFiltersAndSafeSort() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setChannelType(List.of("GREETING", "HMART"));
        request.setSalesPointCode(List.of("SP-1", "SP-2"));
        request.setSort("availableQuantity,asc");
        request.setFilterOperator("or");

        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 14));

        assertThat(query.channelTypes()).containsExactly("GREETING", "HMART");
        assertThat(query.salesPointCodes()).containsExactly("SP-1", "SP-2");
        assertThat(query.sortColumn()).isEqualTo("available_qty");
        assertThat(query.sortDirection()).isEqualTo("ASC");
        assertThat(query.filterOperator()).isEqualTo("OR");
    }

    @Test
    void rejectsUnsupportedFilterOperator() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setFilterOperator("XOR");

        assertThatThrownBy(() -> request.toQuery(LocalDate.of(2026, 8, 14)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejectsUnsupportedEnumAndSortValues() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setChannelType(List.of("NOT_A_CHANNEL"));

        assertThatThrownBy(() -> request.toQuery(LocalDate.of(2026, 8, 14)))
                .isInstanceOf(AppException.class);

        request.setChannelType(List.of("GREETING"));
        request.setSort("updatedAt;drop table inventory_balance");

        assertThatThrownBy(() -> request.toQuery(LocalDate.of(2026, 8, 14)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejectsLegacyRiskAliasesAndPageSizesAboveApiMaximum() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setRiskGrade(List.of("GOOD"));

        assertThatThrownBy(() -> request.toQuery(LocalDate.of(2026, 8, 14)))
                .isInstanceOf(AppException.class);

        request.setRiskGrade(List.of());
        request.setSize(101);

        assertThatThrownBy(() -> request.toQuery(LocalDate.of(2026, 8, 14)))
                .isInstanceOf(AppException.class);

        request.setSize(201);

        assertThatThrownBy(() -> request.toQuery(LocalDate.of(2026, 8, 14)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejectsAssessmentStatusesThatInventoryRowsCannotProduce() {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setAssessmentStatus(List.of("STALE"));

        assertThatThrownBy(() -> request.toQuery(LocalDate.of(2026, 8, 14)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void mapsEverySupportedSortToAWhitelistedSqlColumn() {
        assertSort("updatedAt,desc", "updated_at", "DESC");
        assertSort("productName,asc", "product_name", "ASC");
        assertSort("skuCode,desc", "sku_code", "DESC");
        assertSort("currentQuantity,asc", "current_qty", "ASC");
        assertSort("availableQuantity,desc", "available_qty", "DESC");
        assertSort("reservedQuantity,asc", "reserved_qty", "ASC");
        assertSort("riskGrade,desc", "risk_grade", "DESC");
        assertSort("nearestExpiryDays,asc", "nearest_expiry_days", "ASC");
    }

    @Test
    void calculatesLongOffsetWithoutIntegerOverflow() {
        InventoryQuery query = new InventoryQuery(
                null, List.of(), List.of(), List.of(), List.of(),
                null, List.of(), List.of(), List.of(), "AND",
                100_000_000, 50, "updated_at", "DESC", LocalDate.of(2026, 8, 14)
        );

        long expectedOffset = (100_000_000L - 1) * 50L;
        assertThat(query.offset()).isEqualTo(expectedOffset);
        assertThat(query.getOffset()).isEqualTo(expectedOffset);
    }

    private static void assertSort(String sort, String expectedColumn, String expectedDirection) {
        InventoryQueryRequest request = new InventoryQueryRequest();
        request.setSort(sort);

        InventoryQuery query = request.toQuery(LocalDate.of(2026, 8, 14));

        assertThat(query.sortColumn()).isEqualTo(expectedColumn);
        assertThat(query.sortDirection()).isEqualTo(expectedDirection);
    }
}
