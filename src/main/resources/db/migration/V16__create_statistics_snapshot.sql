CREATE SEQUENCE statistics_snapshot_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE;

CREATE TABLE statistics_snapshot (
    statistics_snapshot_id           NUMBER        NOT NULL,
    sync_job_id                       NUMBER        NOT NULL,
    as_of_date                        DATE          NOT NULL,
    scope_type                        VARCHAR2(20)  NOT NULL,
    warehouse_id                      NUMBER,
    sales_point_id                    NUMBER,
    scope_code                        VARCHAR2(50)  NOT NULL,
    scope_name                        VARCHAR2(200) NOT NULL,
    payload_version                   NUMBER        DEFAULT 1 NOT NULL,
    payload_json                      CLOB          NOT NULL,
    created_at                        TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                        TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    created_by                        NUMBER        NOT NULL,
    updated_by                        NUMBER        NOT NULL,
    is_deleted                        NUMBER(1)     DEFAULT 0 NOT NULL,

    CONSTRAINT pk_statistics_snapshot
        PRIMARY KEY (statistics_snapshot_id),
    CONSTRAINT uq_statistics_snapshot_scope
        UNIQUE (sync_job_id, scope_type, scope_code),
    CONSTRAINT ck_statistics_snapshot_scope
        CHECK (
            scope_type IN (
                'NATIONAL',
                'WAREHOUSE',
                'OFFLINE_STORE',
                'ONLINE_STORE',
                'UNASSIGNED'
            )
        ),
    CONSTRAINT ck_statistics_scope_reference
        CHECK (
            (scope_type = 'NATIONAL'
                AND scope_code = 'ALL'
                AND warehouse_id IS NULL
                AND sales_point_id IS NULL)
            OR (scope_type = 'WAREHOUSE'
                AND scope_code = 'ALL'
                AND warehouse_id IS NULL
                AND sales_point_id IS NULL)
            OR (scope_type = 'WAREHOUSE'
                AND scope_code <> 'ALL'
                AND warehouse_id IS NOT NULL
                AND sales_point_id IS NULL)
            OR (scope_type IN ('OFFLINE_STORE', 'ONLINE_STORE')
                AND scope_code = 'ALL'
                AND warehouse_id IS NULL
                AND sales_point_id IS NULL)
            OR (scope_type IN ('OFFLINE_STORE', 'ONLINE_STORE')
                AND scope_code <> 'ALL'
                AND warehouse_id IS NULL
                AND sales_point_id IS NOT NULL)
            OR (scope_type = 'UNASSIGNED'
                AND scope_code = 'UNASSIGNED'
                AND warehouse_id IS NULL
                AND sales_point_id IS NULL)
        ),
    CONSTRAINT ck_statistics_payload_version
        CHECK (payload_version > 0),
    CONSTRAINT ck_statistics_payload_json
        CHECK (payload_json IS JSON),
    CONSTRAINT ck_statistics_snapshot_deleted
        CHECK (is_deleted IN (0, 1)),
    CONSTRAINT fk_statistics_warehouse
        FOREIGN KEY (warehouse_id)
            REFERENCES warehouse (warehouse_id),
    CONSTRAINT fk_statistics_sales_point
        FOREIGN KEY (sales_point_id)
            REFERENCES sales_point (sales_point_id),
    CONSTRAINT fk_statistics_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user (user_id),
    CONSTRAINT fk_statistics_updated_by
        FOREIGN KEY (updated_by)
            REFERENCES app_user (user_id)
);

CREATE INDEX ix_statistics_snapshot_latest
    ON statistics_snapshot (
        scope_type,
        scope_code,
        is_deleted,
        as_of_date DESC,
        created_at DESC,
        statistics_snapshot_id DESC
    );

CREATE INDEX ix_statistics_warehouse
    ON statistics_snapshot (warehouse_id);

CREATE INDEX ix_statistics_sales_point
    ON statistics_snapshot (sales_point_id);

COMMENT ON TABLE statistics_snapshot
    IS '재고 동기화 작업과 통계 범위별 통계 JSON 스냅샷';
COMMENT ON COLUMN statistics_snapshot.statistics_snapshot_id
    IS '통계 스냅샷 ID';
COMMENT ON COLUMN statistics_snapshot.sync_job_id
    IS '재고 동기화 작업 ID. 동기화 담당 기능과 연결할 논리 식별자';
COMMENT ON COLUMN statistics_snapshot.as_of_date
    IS '재고·수요예측·위험등급 집계 기준일';
COMMENT ON COLUMN statistics_snapshot.scope_type
    IS '통계 범위 유형: NATIONAL, WAREHOUSE, OFFLINE_STORE, ONLINE_STORE, UNASSIGNED';
COMMENT ON COLUMN statistics_snapshot.warehouse_id
    IS '개별 WAREHOUSE 범위가 참조하는 물류센터 ID. 범위 전체 집계는 NULL';
COMMENT ON COLUMN statistics_snapshot.sales_point_id
    IS '개별 OFFLINE_STORE 또는 ONLINE_STORE 범위가 참조하는 판매처 ID. 범위 전체 집계는 NULL';
COMMENT ON COLUMN statistics_snapshot.scope_code
    IS '통계 범위 코드. 범위 전체 집계는 ALL, 미할당은 UNASSIGNED, 개별 위치는 물류센터 또는 판매처 코드';
COMMENT ON COLUMN statistics_snapshot.scope_name
    IS '스냅샷 생성 당시 통계 범위 표시명';
COMMENT ON COLUMN statistics_snapshot.payload_version
    IS '통계 JSON 응답 구조 버전';
COMMENT ON COLUMN statistics_snapshot.payload_json
    IS '재고 통계와 AI 전략 통계 집계 결과를 버전별로 확장하는 JSON';
COMMENT ON COLUMN statistics_snapshot.created_at
    IS '행 생성 시각이자 통계 집계 완료 시각';
COMMENT ON COLUMN statistics_snapshot.updated_at
    IS '행 최종 수정 시각';
COMMENT ON COLUMN statistics_snapshot.created_by
    IS '행을 생성한 시스템 사용자 ID';
COMMENT ON COLUMN statistics_snapshot.updated_by
    IS '행을 최종 수정한 시스템 사용자 ID';
COMMENT ON COLUMN statistics_snapshot.is_deleted
    IS '논리 삭제 여부: 0 활성, 1 삭제';
