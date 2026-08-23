package com.stockit.backend.feature.strategy.calculation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class OracleInClauseBatcherTest {

    @Test
    void splitsMoreThanOneThousandIdsWithoutChangingResultOrder() {
        List<Long> ids = LongStream.rangeClosed(1, 2_001).boxed().toList();
        List<Integer> batchSizes = new ArrayList<>();

        List<Long> result = OracleInClauseBatcher.select(ids, batch -> {
            batchSizes.add(batch.size());
            return batch;
        });

        assertThat(batchSizes).containsExactly(1_000, 1_000, 1);
        assertThat(result).containsExactlyElementsOf(ids);
    }
}
