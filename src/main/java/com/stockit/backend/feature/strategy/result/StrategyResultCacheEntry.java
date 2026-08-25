package com.stockit.backend.feature.strategy.result;

import java.time.LocalDateTime;

public record StrategyResultCacheEntry(
        String cacheKey,
        LocalDateTime expiresAt
) {
}
