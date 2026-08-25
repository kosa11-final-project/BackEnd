-- V22: 동기화 실행과 규칙 기반 위험평가의 lineage만 연결합니다.
-- 사유는 RiskRuleEngine이 계산한 reason_message에 즉시 저장합니다.
ALTER TABLE risk_assessment ADD (
    inventory_sync_run_id NUMBER
);

ALTER TABLE risk_assessment ADD CONSTRAINT fk_risk_sync_run
    FOREIGN KEY (inventory_sync_run_id) REFERENCES inventory_sync_run (inventory_sync_run_id) ON DELETE SET NULL;

CREATE INDEX ix_risk_sync_run ON risk_assessment (inventory_sync_run_id);
