package com.stockit.backend.feature.strategy.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListQuery;

class AiStrategyCaseListRequestTest {

    @Test
    void appliesDefaultsAndNormalizesAllStatus() {
        AiStrategyCaseListRequest request = new AiStrategyCaseListRequest();
        request.setQuery("   ");
        request.setStatus(" all ");

        AiStrategyCaseListQuery query = request.toQuery();

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.searchText()).isNull();
        assertThat(query.caseStatus()).isNull();
        assertThat(query.sortDirection()).isEqualTo("DESC");
    }

    @Test
    void createsInclusiveBusinessDateRangeAndNumericCaseSearch() {
        AiStrategyCaseListRequest request = new AiStrategyCaseListRequest();
        request.setQuery(" 123 ");
        request.setStatus(" generated ");
        request.setFrom(LocalDate.of(2026, 8, 20));
        request.setTo(LocalDate.of(2026, 8, 24));
        request.setChannelType("GREETING");
        request.setWarehouseCode(" GYEONGIN_1 ");
        request.setStrategyFrom(LocalDate.of(2026, 8, 21));
        request.setStrategyTo(LocalDate.of(2026, 8, 31));
        request.setSort("createdAt,asc");

        AiStrategyCaseListQuery query = request.toQuery();

        assertThat(query.strategyCaseId()).isEqualTo(123L);
        assertThat(query.searchText()).isEqualTo("123");
        assertThat(query.caseStatus()).isEqualTo("GENERATED");
        assertThat(query.createdFrom()).isEqualTo("2026-08-20T00:00:00");
        assertThat(query.createdToExclusive()).isEqualTo("2026-08-25T00:00:00");
        assertThat(query.channelType()).isEqualTo("GREETING");
        assertThat(query.warehouseCode()).isEqualTo("GYEONGIN_1");
        assertThat(query.strategyFrom()).isEqualTo("2026-08-21");
        assertThat(query.strategyTo()).isEqualTo("2026-08-31");
        assertThat(query.sortDirection()).isEqualTo("ASC");
    }

    @Test
    void escapesLikeWildcardsAsLiteralSearchText() {
        AiStrategyCaseListRequest request = new AiStrategyCaseListRequest();
        request.setQuery("SKU_10%");

        assertThat(request.toQuery().searchText()).isEqualTo("SKU\\_10\\%");
    }

    @Test
    void rejectsExpiredStatusReversedDatesAndUnsupportedSort() {
        AiStrategyCaseListRequest request = new AiStrategyCaseListRequest();
        request.setStatus("EXPIRED");
        assertInvalid(request);

        request = new AiStrategyCaseListRequest();
        request.setFrom(LocalDate.of(2026, 8, 25));
        request.setTo(LocalDate.of(2026, 8, 24));
        assertInvalid(request);

        request = new AiStrategyCaseListRequest();
        request.setStrategyFrom(LocalDate.of(2026, 8, 25));
        request.setStrategyTo(LocalDate.of(2026, 8, 24));
        assertInvalid(request);

        request = new AiStrategyCaseListRequest();
        request.setChannelType("ONLINE");
        assertInvalid(request);

        request = new AiStrategyCaseListRequest();
        request.setSort("caseName,desc");
        assertInvalid(request);
    }

    private static void assertInvalid(AiStrategyCaseListRequest request) {
        assertThatThrownBy(request::toQuery)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
