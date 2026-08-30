package com.stockit.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.RequestCancelledException;
import com.stockit.backend.common.web.RequestCancellationContext;
import com.stockit.backend.common.web.RequestCancellationToken;

class RequestCancellationMyBatisInterceptorTest {

    @AfterEach
    void clearContext() {
        RequestCancellationContext.clear();
    }

    @Test
    void convertsAStatementFailureAfterCancellationToControlException() throws Exception {
        StatementHandler handler = mock(StatementHandler.class);
        Statement statement = mock(Statement.class);
        ResultHandler resultHandler = mock(ResultHandler.class);
        RequestCancellationToken token = new RequestCancellationToken();
        when(handler.query(statement, resultHandler)).thenAnswer(ignored -> {
            token.cancel();
            throw new SQLException("cancelled");
        });
        RequestCancellationContext.bind(token);

        Method method = StatementHandler.class.getMethod("query", Statement.class, ResultHandler.class);
        Invocation invocation = new Invocation(handler, method, new Object[]{statement, resultHandler});

        assertThatThrownBy(() -> new RequestCancellationMyBatisInterceptor().intercept(invocation))
                .isInstanceOf(RequestCancelledException.class);
        verify(handler).query(statement, resultHandler);
    }

    @Test
    void doesNotExecuteAQueryWhenItsTokenWasAlreadyCancelled() throws Exception {
        StatementHandler handler = mock(StatementHandler.class);
        Statement statement = mock(Statement.class);
        ResultHandler resultHandler = mock(ResultHandler.class);
        RequestCancellationToken token = new RequestCancellationToken();
        RequestCancellationContext.bind(token);
        token.cancel();

        Method method = StatementHandler.class.getMethod("query", Statement.class, ResultHandler.class);
        Invocation invocation = new Invocation(handler, method, new Object[]{statement, resultHandler});

        assertThatThrownBy(() -> new RequestCancellationMyBatisInterceptor().intercept(invocation))
                .isInstanceOf(RequestCancelledException.class);
        verify(handler, never()).query(statement, resultHandler);
    }

    @Test
    void passesThroughSuccessfulQueriesAndUnregistersTheirStatement() throws Throwable {
        StatementHandler handler = mock(StatementHandler.class);
        Statement statement = mock(Statement.class);
        ResultHandler resultHandler = mock(ResultHandler.class);
        when(handler.query(statement, resultHandler)).thenReturn(List.of());

        RequestCancellationToken token = new RequestCancellationToken();
        RequestCancellationContext.bind(token);
        Method method = StatementHandler.class.getMethod("query", Statement.class, ResultHandler.class);
        Invocation invocation = new Invocation(handler, method, new Object[]{statement, resultHandler});

        new RequestCancellationMyBatisInterceptor().intercept(invocation);
        token.cancel();

        org.mockito.Mockito.verify(statement, org.mockito.Mockito.never()).cancel();
    }
}
