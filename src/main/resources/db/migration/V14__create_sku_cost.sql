CREATE SEQUENCE sku_cost_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE;

CREATE TABLE sku_cost (
    sku_cost_id    NUMBER        NOT NULL,
    sku_id         NUMBER        NOT NULL,
    unit_cost      NUMBER(18,2)  NOT NULL,
    effective_from DATE          NOT NULL,
    effective_to   DATE,
    created_at     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    created_by     NUMBER        NOT NULL,
    updated_by     NUMBER        NOT NULL,
    is_deleted     NUMBER(1)     DEFAULT 0 NOT NULL,

    CONSTRAINT pk_sku_cost
        PRIMARY KEY (sku_cost_id),
    CONSTRAINT uq_sku_cost_effective
        UNIQUE (sku_id, effective_from),
    CONSTRAINT fk_sku_cost_sku
        FOREIGN KEY (sku_id)
            REFERENCES sku (sku_id),
    CONSTRAINT fk_sku_cost_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user (user_id),
    CONSTRAINT fk_sku_cost_updated_by
        FOREIGN KEY (updated_by)
            REFERENCES app_user (user_id),
    CONSTRAINT ck_sku_cost_nonnegative
        CHECK (unit_cost >= 0),
    CONSTRAINT ck_sku_cost_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_sku_cost_deleted
        CHECK (is_deleted IN (0, 1))
);

COMMENT ON TABLE sku_cost IS '판매처와 독립적으로 관리하는 SKU 기준 원가 이력';
COMMENT ON COLUMN sku_cost.sku_cost_id IS 'SKU 원가 ID';
COMMENT ON COLUMN sku_cost.sku_id IS '원가가 적용되는 SKU ID';
COMMENT ON COLUMN sku_cost.unit_cost IS 'SKU 재고 1단위당 기준 원가';
COMMENT ON COLUMN sku_cost.effective_from IS '원가 적용 시작일';
COMMENT ON COLUMN sku_cost.effective_to IS '원가 적용 종료일. 현재 원가는 NULL';
COMMENT ON COLUMN sku_cost.created_at IS '행 생성 시각';
COMMENT ON COLUMN sku_cost.updated_at IS '행 최종 수정 시각';
COMMENT ON COLUMN sku_cost.created_by IS '행을 생성한 사용자 ID';
COMMENT ON COLUMN sku_cost.updated_by IS '행을 최종 수정한 사용자 ID';
COMMENT ON COLUMN sku_cost.is_deleted IS '논리 삭제 여부: 0 활성, 1 삭제';
