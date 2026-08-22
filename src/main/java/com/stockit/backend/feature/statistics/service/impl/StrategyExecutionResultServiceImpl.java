package com.stockit.backend.feature.statistics.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stockit.backend.feature.statistics.mapper.StrategyStatisticsMapper;
import com.stockit.backend.feature.statistics.service.StrategyExecutionResultService;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionDueResultVO;
import com.stockit.backend.feature.statistics.vo.StrategyExecutionStartCandidateVO;

@Service
public class StrategyExecutionResultServiceImpl implements StrategyExecutionResultService {
    private static final Logger log = LoggerFactory.getLogger(StrategyExecutionResultServiceImpl.class);

    private final StrategyStatisticsMapper mapper;

    public StrategyExecutionResultServiceImpl(StrategyStatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void process(LocalDate businessDate) {
        List<StrategyExecutionStartCandidateVO> starts = mapper.selectExecutionStartCandidates(businessDate);
        for (StrategyExecutionStartCandidateVO candidate : starts) {
            mapper.insertExecutionStartResult(candidate);
        }

        List<StrategyExecutionDueResultVO> dueResults = mapper.selectDueExecutionResults(businessDate);
        for (StrategyExecutionDueResultVO result : dueResults) {
            BigDecimal achievementRate = result.getGoalActualValue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(result.getGoalTargetValue(), 6, RoundingMode.HALF_UP);
            BigDecimal savings = result.getStartExpectedDisposalQty()
                    .subtract(result.getEndExpectedDisposalQty())
                    .multiply(result.getStartUnitCost())
                    .setScale(2, RoundingMode.HALF_UP);
            int updated = mapper.completeExecutionResult(result, achievementRate, savings);
            if (updated == 0) {
                log.info("strategy result was already completed: finalSelectionId={}",
                        result.getFinalSelectionId());
            }
        }

        log.info("strategy result lifecycle processed: date={}, started={}, completed={}",
                businessDate, starts.size(), dueResults.size());
    }
}
