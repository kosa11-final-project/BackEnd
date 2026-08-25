ALTER TABLE strategy_case ADD (
    generation_stage VARCHAR2(30)
);

ALTER TABLE strategy_case
    ADD CONSTRAINT ck_strategy_case_gen_stage
        CHECK (
            generation_stage IS NULL
            OR generation_stage IN (
                'FORECASTING',
                'STRATEGY_GENERATING',
                'COMPARISON_READY'
            )
        );

COMMENT ON COLUMN strategy_case.generation_stage
    IS 'AI 전략 생성 진행 단계: FORECASTING, STRATEGY_GENERATING, COMPARISON_READY. 작업 발행 후 Worker 처리 전에는 NULL';
