package com.stockit.backend.common.web;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.stockit.backend.common.exception.RequestCancelledException;

/**
 * 하나의 HTTP 조회 요청과 실행 중인 JDBC Statement를 연결하는 취소 토큰입니다.
 *
 * <p>같은 조회 scope로 새 요청이 들어오면 coordinator가 토큰을 취소합니다.
 * MyBatis interceptor는 현재 토큰에 Statement를
 * 등록하고, 취소 시 JDBC {@link Statement#cancel()}을 호출합니다.</p>
 */
public final class RequestCancellationToken {

    public static final String REQUEST_ATTRIBUTE = RequestCancellationToken.class.getName();

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Set<Statement> statements = ConcurrentHashMap.newKeySet();

    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 실행 중인 Statement를 등록합니다. 등록과 동시에 취소된 경우에도 race 없이 cancel을 보장합니다.
     */
    public boolean register(Statement statement) {
        if (statement == null) return false;
        if (cancelled.get()) {
            cancelStatement(statement);
            return false;
        }

        statements.add(statement);
        if (cancelled.get() && statements.remove(statement)) {
            cancelStatement(statement);
            return false;
        }
        return true;
    }

    public void unregister(Statement statement) {
        if (statement != null) statements.remove(statement);
    }

    /**
     * 토큰을 멱등적으로 취소하고 현재 등록된 모든 JDBC Statement를 중단합니다.
     */
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) return;
        statements.forEach(this::cancelStatement);
        statements.clear();
    }

    public void throwIfCancelled() {
        if (isCancelled()) throw new RequestCancelledException();
    }

    private void cancelStatement(Statement statement) {
        try {
            statement.cancel();
        } catch (SQLException | RuntimeException ignored) {
            // 취소 시점의 연결 종료·드라이버 오류는 원래 조회 오류로 전파하지 않습니다.
        }
    }
}
