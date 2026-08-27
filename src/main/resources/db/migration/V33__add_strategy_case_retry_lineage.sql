ALTER TABLE strategy_case ADD (
    retry_parent_case_id NUMBER
);

ALTER TABLE strategy_case
    ADD CONSTRAINT fk_strat_case_retry_parent
        FOREIGN KEY (retry_parent_case_id)
            REFERENCES strategy_case (strategy_case_id);

ALTER TABLE strategy_case
    ADD CONSTRAINT uq_strat_case_retry_parent
        UNIQUE (retry_parent_case_id);

ALTER TABLE strategy_case
    ADD CONSTRAINT ck_strat_case_retry_not_self
        CHECK (
            retry_parent_case_id IS NULL
            OR retry_parent_case_id <> strategy_case_id
        );

COMMENT ON COLUMN strategy_case.retry_parent_case_id
    IS '사용자 재시도로 생성된 Case의 직전 실패 Case ID. 최초 생성 Case는 NULL';
