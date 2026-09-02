package com.stockit.backend.feature.strategy.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.strategy.domain.StrategyCaseStatus;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseListItemResponse;
import com.stockit.backend.feature.strategy.dto.response.AiStrategyCaseListPageResponse;
import com.stockit.backend.feature.strategy.mapper.AiStrategyCaseListMapper;
import com.stockit.backend.feature.strategy.result.StrategyResultProperties;
import com.stockit.backend.feature.strategy.service.AiStrategyCaseListService;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseListQuery;
import com.stockit.backend.feature.strategy.vo.AiStrategyCaseStatusCountVO;
import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

@Service
@Transactional(readOnly = true)
public class AiStrategyCaseListServiceImpl implements AiStrategyCaseListService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final AiStrategyCaseListMapper mapper;
    private final StrategyResultProperties resultProperties;
    private final Clock clock;

    @Autowired
    public AiStrategyCaseListServiceImpl(
            AiStrategyCaseListMapper mapper,
            StrategyResultProperties resultProperties
    ) {
        this(mapper, resultProperties, Clock.system(BUSINESS_ZONE));
    }

    AiStrategyCaseListServiceImpl(
            AiStrategyCaseListMapper mapper,
            StrategyResultProperties resultProperties,
            Clock clock
    ) {
        this.mapper = mapper;
        this.resultProperties = resultProperties;
        this.clock = clock;
    }

    @Override
    public AiStrategyCaseListPageResponse findAll(AiStrategyCaseListQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.warehouseCode() != null
                && mapper.countActiveWarehouseCode(query.warehouseCode()) == 0) {
            throw new AppException(ErrorCode.INVALID_PARAMETER);
        }
        LocalDateTime visibleAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        LocalDateTime visibleFrom = visibleAt.minus(resultProperties.getTtl());

        long totalElements = mapper.countCases(query, visibleAt, visibleFrom);
        List<AiStrategyCaseListItemResponse> content = totalElements == 0
                ? List.of()
                : safe(mapper.selectCases(query, visibleAt, visibleFrom)).stream()
                        .map(AiStrategyCaseListItemResponse::from)
                        .toList();

        Map<StrategyCaseStatus, Long> counts = new EnumMap<>(StrategyCaseStatus.class);
        for (AiStrategyCaseStatusCountVO row : safe(
                mapper.countCasesByStatus(query, visibleAt, visibleFrom)
        )) {
            if (row.getCaseStatus() != null) {
                counts.put(row.getCaseStatus(), row.getStatusCount());
            }
        }
        long generating = counts.getOrDefault(StrategyCaseStatus.GENERATING, 0L);
        long generated = counts.getOrDefault(StrategyCaseStatus.GENERATED, 0L);
        long failed = counts.getOrDefault(StrategyCaseStatus.GENERATION_FAILED, 0L);
        AiStrategyCaseListPageResponse.StatusCounts statusCounts =
                new AiStrategyCaseListPageResponse.StatusCounts(
                        generating + generated + failed,
                        generating,
                        generated,
                        failed
                );

        int totalPages = totalElements == 0
                ? 0
                : (int) ((totalElements + query.size() - 1) / query.size());
        return new AiStrategyCaseListPageResponse(
                content,
                statusCounts,
                query.page(),
                query.size(),
                totalElements,
                totalPages,
                totalPages == 0 || query.page() == 0,
                totalPages == 0 || query.page() >= totalPages - 1
        );
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
