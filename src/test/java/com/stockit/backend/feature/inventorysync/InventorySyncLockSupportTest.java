package com.stockit.backend.feature.inventorysync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;

class InventorySyncLockSupportTest {

    @Test
    void recognizesSpringLockAndQueryTimeoutExceptions() {
        assertThat(InventorySyncLockSupport.isLockWaitFailure(
                new CannotAcquireLockException("ORA-30006: resource busy; acquire with WAIT timeout specified")))
                .isTrue();
        assertThat(InventorySyncLockSupport.isLockWaitFailure(
                new QueryTimeoutException("statement timed out while waiting for a row lock")))
                .isTrue();
    }

    @Test
    void recognizesOracleLockErrorCodesInWrappedDatabaseExceptions() {
        assertThat(InventorySyncLockSupport.isLockWaitFailure(
                new org.springframework.jdbc.BadSqlGrammarException(
                        "lock", "SELECT ...", new java.sql.SQLException("ORA-00054: resource busy"))))
                .isTrue();
    }
}
