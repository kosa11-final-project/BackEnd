package com.stockit.backend.feature.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import com.stockit.backend.common.exception.RequestCancelledException;
import com.stockit.backend.common.web.RequestCancellationContext;

class InventoryQueryCancellationCoordinatorTest {

    @Test
    void cancelsThePreviousRequestOnlyWithinTheSameOperationAndSession() throws Exception {
        InventoryQueryCancellationCoordinator coordinator = new InventoryQueryCancellationCoordinator();
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest firstRequest = requestWithSession(session);
        MockHttpServletRequest secondRequest = requestWithSession(session);
        CountDownLatch firstStarted = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = executor.submit(() -> coordinator.execute(
                    "inventory-list",
                    firstRequest,
                    () -> {
                        firstStarted.countDown();
                        while (!RequestCancellationContext.current().isCancelled()) {
                            Thread.onSpinWait();
                        }
                        return "old";
                    }
            ));

            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.execute("inventory-list", secondRequest, () -> "latest")).isEqualTo("latest");

            assertThatThrownBy(first::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RequestCancelledException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void doesNotCancelAConcurrentSummaryRequest() throws Exception {
        InventoryQueryCancellationCoordinator coordinator = new InventoryQueryCancellationCoordinator();
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest listRequest = requestWithSession(session);
        MockHttpServletRequest summaryRequest = requestWithSession(session);

        assertThat(coordinator.execute("inventory-list", listRequest, () -> "list")).isEqualTo("list");
        assertThat(coordinator.execute("inventory-summary", summaryRequest, () -> "summary")).isEqualTo("summary");
    }

    private static MockHttpServletRequest requestWithSession(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }
}
