package com.stockit.backend.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class RequestCancellationTokenTest {

    @Test
    void cancelsRegisteredStatementsOnlyOnce() throws Exception {
        Statement statement = mock(Statement.class);
        RequestCancellationToken token = new RequestCancellationToken();

        assertThat(token.register(statement)).isTrue();
        token.cancel();
        token.cancel();

        assertThat(token.isCancelled()).isTrue();
        verify(statement, times(1)).cancel();
    }

    @Test
    void cancelsAStatementRegisteredAfterTheRequestWasCancelled() throws Exception {
        Statement statement = mock(Statement.class);
        RequestCancellationToken token = new RequestCancellationToken();

        token.cancel();
        assertThat(token.register(statement)).isFalse();

        verify(statement).cancel();
    }
}
