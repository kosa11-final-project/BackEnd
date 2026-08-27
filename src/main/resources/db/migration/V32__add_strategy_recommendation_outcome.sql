ALTER TABLE strategy_case
    ADD recommendation_outcome VARCHAR2(30);

ALTER TABLE strategy_case
    ADD CONSTRAINT ck_strategy_case_recommendation_outcome
        CHECK (
            recommendation_outcome IS NULL
            OR recommendation_outcome IN (
                'OPTIONS_GENERATED',
                'MAINTAIN_CURRENT_STATE'
            )
        );

ALTER TABLE strategy_case
    ADD CONSTRAINT ck_strategy_case_outcome_stage
        CHECK (
            recommendation_outcome IS NULL
            OR generation_stage = 'COMPARISON_READY'
        );

COMMENT ON COLUMN strategy_case.recommendation_outcome
    IS '정상 생성 완료 결과 유형: OPTIONS_GENERATED, MAINTAIN_CURRENT_STATE. 기존 또는 미완료 데이터는 NULL';
