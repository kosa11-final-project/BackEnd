package com.stockit.backend.feature.statistics.service;

import java.time.LocalDate;

public interface StrategyExecutionResultService {
    void process(LocalDate businessDate);
}
