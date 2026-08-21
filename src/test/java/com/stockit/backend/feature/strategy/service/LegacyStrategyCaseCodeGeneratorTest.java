package com.stockit.backend.feature.strategy.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LegacyStrategyCaseCodeGeneratorTest {

    private final LegacyStrategyCaseCodeGenerator generator =
            new LegacyStrategyCaseCodeGenerator();

    @Test
    void generatesUniqueLegacyValueWithinDatabaseLength() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).matches("SC-[0-9a-f]{32}");
        assertThat(first).hasSizeLessThanOrEqualTo(50);
        assertThat(second).isNotEqualTo(first);
    }
}
