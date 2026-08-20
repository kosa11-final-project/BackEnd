package com.stockit.backend.feature.strategy.domain;

/**
 * AI가 제안하거나 후속 단계에서 실행할 수 있는 재고 전략 유형
 */
public enum StrategyType {
    REALLOCATION(true),
    RT_TRANSFER(true),
    PRICE_DISCOUNT(true),
    PROMOTION_STOP(false),
    CHANNEL_EXPANSION(true),
    CHANNEL_CONCENTRATION(true),
    REPLENISHMENT_REQUEST(false),
    SAFETY_STOCK_ADJUSTMENT(false);

    private final boolean supportedForGeneration;

    StrategyType(boolean supportedForGeneration) {
        this.supportedForGeneration = supportedForGeneration;
    }

    public boolean isSupportedForGeneration() {
        return supportedForGeneration;
    }
}
