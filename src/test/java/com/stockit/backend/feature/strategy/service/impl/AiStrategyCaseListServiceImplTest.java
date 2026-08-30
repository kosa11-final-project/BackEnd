package com.stockit.backend.feature.strategy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.domain.StrategyRecommendationOutcome;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseListPageResponse;
import com.stockit.backend.feature.strategy.mapper.AiStrategyCaseListMapper;
import com.stockit.backend.feature.strategy.result.StrategyResultProperties;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseListService;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListQuery;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListVO;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseStatusCountVO;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AiStrategyCaseListServiceImplTest {

    private static final LocalDateTime VISIBLE_AT = LocalDateTime.of(2026, 8, 24, 10, 0);
    private static final LocalDateTime VISIBLE_FROM = LocalDateTime.of(2026, 8, 21, 10, 0);

    @Mock
    private AiStrategyCaseListMapper mapper;

    private AiStrategyCaseListService service;

    @BeforeEach
    void setUp() {
        StrategyResultProperties properties = new StrategyResultProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T01:00:00Z"), ZoneId.of("UTC"));
        service = new AiStrategyCaseListServiceImpl(mapper, properties, clock);
    }

    @Test
    void returnsPageAndStatusCountsUsingSameThreeDayVisibilityWindow() {
        AiStrategyCaseListQuery query = query(0, 2, "GENERATED");
        AiStrategyCaseListVO item = item(102L, StrategyCaseStatus.GENERATED);
        when(mapper.countCases(query, VISIBLE_AT, VISIBLE_FROM)).thenReturn(3L);
        when(mapper.selectCases(query, VISIBLE_AT, VISIBLE_FROM)).thenReturn(List.of(item));
        when(mapper.countCasesByStatus(query, VISIBLE_AT, VISIBLE_FROM)).thenReturn(List.of(
                count(StrategyCaseStatus.GENERATING, 2),
                count(StrategyCaseStatus.GENERATED, 3),
                count(StrategyCaseStatus.GENERATION_FAILED, 1)
        ));

        AiStrategyCaseListPageResponse result = service.findAll(query);

        assertThat(result.content()).singleElement().satisfies(value -> {
            assertThat(value.strategyCaseId()).isEqualTo(102L);
            assertThat(value.sku().skuCode()).isEqualTo("SKU-1");
            assertThat(value.requester().userName()).isEqualTo("이주영");
            assertThat(value.recommendationOutcome())
                    .isEqualTo(StrategyRecommendationOutcome.OPTIONS_GENERATED);
            assertThat(value.sku().categoryPathLabel()).isEqualTo("대분류 > 중분류 > 소분류");
        });
        assertThat(result.statusCounts().all()).isEqualTo(6);
        assertThat(result.statusCounts().generating()).isEqualTo(2);
        assertThat(result.statusCounts().generated()).isEqualTo(3);
        assertThat(result.statusCounts().generationFailed()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.last()).isFalse();
    }

    @Test
    void returnsEmptyPageButStillLoadsUnfilteredStatusCounts() {
        AiStrategyCaseListQuery query = query(3, 10, "GENERATED");
        when(mapper.countCases(query, VISIBLE_AT, VISIBLE_FROM)).thenReturn(0L);
        when(mapper.countCasesByStatus(query, VISIBLE_AT, VISIBLE_FROM)).thenReturn(List.of(
                count(StrategyCaseStatus.GENERATING, 1)
        ));

        AiStrategyCaseListPageResponse result = service.findAll(query);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalPages()).isZero();
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
        assertThat(result.statusCounts().all()).isOne();
        verify(mapper, never()).selectCases(query, VISIBLE_AT, VISIBLE_FROM);
    }

    @Test
    void rejectsUnknownWarehouseCode() {
        AiStrategyCaseListQuery query = new AiStrategyCaseListQuery(
                0, 10, null, null, null, null, null,
                null, "UNKNOWN", null, null, "DESC"
        );
        when(mapper.countActiveWarehouseCode("UNKNOWN")).thenReturn(0);

        assertThatThrownBy(() -> service.findAll(query))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
        verify(mapper, never()).countCases(query, VISIBLE_AT, VISIBLE_FROM);
    }

    @Test
    void returnsNullCategoryPathWhenCategoryDoesNotExist() {
        AiStrategyCaseListQuery query = query(0, 10, null);
        AiStrategyCaseListVO item = item(103L, StrategyCaseStatus.GENERATING);
        item.setCategoryId(null);
        when(mapper.countCases(query, VISIBLE_AT, VISIBLE_FROM)).thenReturn(1L);
        when(mapper.selectCases(query, VISIBLE_AT, VISIBLE_FROM)).thenReturn(List.of(item));

        AiStrategyCaseListPageResponse result = service.findAll(query);

        assertThat(result.content()).singleElement().satisfies(value -> {
            assertThat(value.sku().category()).isNull();
            assertThat(value.sku().categoryPathLabel()).isNull();
        });
    }

    private static AiStrategyCaseListQuery query(int page, int size, String status) {
        return new AiStrategyCaseListQuery(page, size, null, null, status, null, null, "DESC");
    }

    private static AiStrategyCaseListVO item(Long id, StrategyCaseStatus status) {
        AiStrategyCaseListVO value = new AiStrategyCaseListVO();
        value.setStrategyCaseId(id);
        value.setCaseName("테스트 전략");
        value.setCaseStatus(status);
        value.setRecommendationOutcome(
                StrategyRecommendationOutcome.OPTIONS_GENERATED
        );
        value.setSkuId(1L);
        value.setSkuCode("SKU-1");
        value.setSkuName("테스트 SKU");
        value.setCategoryId(3L);
        value.setCategoryName("소분류");
        value.setCategoryLevel(3);
        value.setParentCategoryName("중분류");
        value.setGrandparentCategoryName("대분류");
        value.setRequesterId(7L);
        value.setRequesterName("이주영");
        value.setCreatedAt(VISIBLE_AT.minusHours(1));
        return value;
    }

    private static AiStrategyCaseStatusCountVO count(StrategyCaseStatus status, long count) {
        AiStrategyCaseStatusCountVO value = new AiStrategyCaseStatusCountVO();
        value.setCaseStatus(status);
        value.setStatusCount(count);
        return value;
    }
}
