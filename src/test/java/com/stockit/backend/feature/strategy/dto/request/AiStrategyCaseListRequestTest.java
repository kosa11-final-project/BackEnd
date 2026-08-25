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
        request.setSort("createdAt,asc");

        AiStrategyCaseListQuery query = request.toQuery();

        assertThat(query.strategyCaseId()).isEqualTo(123L);
        assertThat(query.searchText()).isEqualTo("123");
        assertThat(query.caseStatus()).isEqualTo("GENERATED");
        assertThat(query.createdFrom()).isEqualTo("2026-08-20T00:00:00");
        assertThat(query.createdToExclusive()).isEqualTo("2026-08-25T00:00:00");
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
        request.setSort("caseName,desc");
        assertInvalid(request);
    }

    private static void assertInvalid(AiStrategyCaseListRequest request) {
        assertThatThrownBy(request::toQuery)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
