ALTER TABLE final_strategy_selection ADD (
    last_synced_at TIMESTAMP
);

COMMENT ON COLUMN final_strategy_selection.last_synced_at
    IS '최종 선택 전략 성과의 최근 동기화 완료 시각. 동기화 전에는 NULL';
