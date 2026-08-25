package com.stockit.backend.feature.strategy.dto.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;
import com.stockit.backend.feature.strategy.vo.StrategyExecutionQuery;

class StrategyExecutionListRequestTest {

    @Test
    void appliesDefaultsAndIgnoresBlankSearchTerm() {
        StrategyExecutionListRequest request = new StrategyExecutionListRequest();
        request.setQuery("   ");

        StrategyExecutionQuery query = request.toQuery();

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.query()).isNull();
        assertThat(query.sortDirection()).isEqualTo("DESC");
    }

    @Test
    void trimsAndNormalizesSearchAndFilters() {
        StrategyExecutionListRequest request = new StrategyExecutionListRequest();
        request.setQuery("  두부  ");
        request.setStatus(" executing ");
        request.setActionType(" price_discount ");
        request.setSort("establishedAt,asc");

        StrategyExecutionQuery query = request.toQuery();

        assertThat(query.query()).isEqualTo("두부");
        assertThat(query.caseStatus()).isEqualTo("EXECUTING");
        assertThat(query.actionType()).isEqualTo("PRICE_DISCOUNT");
        assertThat(query.sortDirection()).isEqualTo("ASC");
    }

    @Test
    void rejectsUnsupportedFilterAndSortValues() {
        StrategyExecutionListRequest request = new StrategyExecutionListRequest();
        request.setStatus("UNKNOWN");
        assertInvalid(request);

        request = new StrategyExecutionListRequest();
        request.setActionType("UNKNOWN");
        assertInvalid(request);

        request = new StrategyExecutionListRequest();
        request.setSort("number,desc");
        assertInvalid(request);
    }

    private static void assertInvalid(StrategyExecutionListRequest request) {
        assertThatThrownBy(request::toQuery)
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
    }
}
