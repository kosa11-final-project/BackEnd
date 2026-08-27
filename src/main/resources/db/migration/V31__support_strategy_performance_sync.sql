ALTER TABLE strategy_performance
    ADD CONSTRAINT uq_strategy_perf_option_date
        UNIQUE (strategy_option_id, performance_date);

CREATE TABLE strategy_perf_sync_mutex (
    mutex_id   NUMBER(1) NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_strategy_perf_sync_mutex PRIMARY KEY (mutex_id),
    CONSTRAINT ck_strategy_perf_sync_single CHECK (mutex_id = 1)
);

INSERT INTO strategy_perf_sync_mutex (mutex_id) VALUES (1);

COMMENT ON TABLE strategy_perf_sync_mutex
    IS '전략 성과 수동 동기화를 단일 실행으로 직렬화하는 singleton mutex';
COMMENT ON COLUMN strategy_perf_sync_mutex.mutex_id
    IS '항상 1인 singleton 잠금 행';
