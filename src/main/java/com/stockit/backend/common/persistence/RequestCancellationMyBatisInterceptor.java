package com.stockit.backend.common.persistence;

import java.sql.Statement;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

import com.stockit.backend.common.exception.RequestCancelledException;
import com.stockit.backend.common.web.RequestCancellationContext;
import com.stockit.backend.common.web.RequestCancellationToken;

/** 현재 HTTP 조회 요청의 취소 토큰과 MyBatis JDBC Statement를 연결합니다. */
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class})
})
public class RequestCancellationMyBatisInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        RequestCancellationToken token = RequestCancellationContext.current();
        if (token == null || !(invocation.getArgs()[0] instanceof Statement statement)) {
            return invocation.proceed();
        }

        if (!token.register(statement)) throw new RequestCancelledException();
        try {
            token.throwIfCancelled();
            return invocation.proceed();
        } catch (Throwable error) {
            if (token.isCancelled()) throw new RequestCancelledException(error);
            throw error;
        } finally {
            token.unregister(statement);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
