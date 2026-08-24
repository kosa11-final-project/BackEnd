package com.stockit.backend.feature.inventorysync;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

/** Oracle row-lock 대기를 동기화 API의 충돌 응답으로 변환하는 공통 지원 클래스입니다. */
public final class InventorySyncLockSupport {
    private static final String LOCK_CONFLICT_MESSAGE =
            "동기화 대상이 다른 작업에 의해 잠겨 있습니다. 잠시 후 다시 시도해 주세요.";

    private InventorySyncLockSupport() {
    }

    public static boolean isLockWaitFailure(DataAccessException exception) {
        if (exception instanceof PessimisticLockingFailureException
                || exception instanceof QueryTimeoutException) {
            return true;
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && (message.contains("ORA-00054") || message.contains("ORA-30006"))) {
                return true;
            }
        }
        return false;
    }

    public static AppException conflict() {
        return new AppException(ErrorCode.INVENTORY_SYNC_CONFLICT, LOCK_CONFLICT_MESSAGE);
    }
}
