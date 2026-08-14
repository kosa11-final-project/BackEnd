-- 위험등급 판정 테이블에 위험점수 추가
ALTER TABLE risk_assessment
    ADD risk_score NUMBER(5,2) DEFAULT 0 NOT NULL;

ALTER TABLE risk_assessment
    ADD CONSTRAINT ck_risk_assessment_score
        CHECK (risk_score BETWEEN 0 AND 100);

COMMENT ON COLUMN risk_assessment.risk_score
    IS '위험 점수(0~100점, 점수가 높을수록 우선 처리 대상)';


-- 판매처별 SKU 가격 테이블에서 포장비 제거
ALTER TABLE sku_channel_price
DROP COLUMN packaging_cost;