CREATE SEQUENCE dashboard_snapshot_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE;

CREATE TABLE dashboard_snapshot (
    dashboard_snapshot_id NUMBER       NOT NULL,
    sync_job_id           NUMBER       NOT NULL,
    payload_version       NUMBER       DEFAULT 1 NOT NULL,
    payload_json          CLOB         NOT NULL,
    created_at            TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at            TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    created_by            NUMBER       NOT NULL,
    updated_by            NUMBER       NOT NULL,
    is_deleted            NUMBER(1)    DEFAULT 0 NOT NULL,

    CONSTRAINT pk_dashboard_snapshot
        PRIMARY KEY (dashboard_snapshot_id),
    CONSTRAINT uq_dashboard_snapshot_sync
        UNIQUE (sync_job_id),
    CONSTRAINT ck_dashboard_payload_version
        CHECK (payload_version > 0),
    CONSTRAINT ck_dashboard_payload_json
        CHECK (payload_json IS JSON),
    CONSTRAINT ck_dashboard_snapshot_deleted
        CHECK (is_deleted IN (0, 1)),
    CONSTRAINT fk_dash_snapshot_created_by
        FOREIGN KEY (created_by)
            REFERENCES app_user (user_id),
    CONSTRAINT fk_dash_snapshot_updated_by
        FOREIGN KEY (updated_by)
            REFERENCES app_user (user_id)
);

CREATE INDEX ix_dashboard_snapshot_latest
    ON dashboard_snapshot (is_deleted, created_at DESC, dashboard_snapshot_id DESC);

COMMENT ON TABLE dashboard_snapshot IS '재고 동기화 작업별 대시보드 JSON 스냅샷';
COMMENT ON COLUMN dashboard_snapshot.sync_job_id IS '재고 동기화 작업 ID. 동기화 담당 기능과 연결할 논리 식별자';
COMMENT ON COLUMN dashboard_snapshot.payload_version IS '대시보드 JSON 응답 구조 버전';
COMMENT ON COLUMN dashboard_snapshot.payload_json IS '대시보드 전체 집계 결과 JSON';
COMMENT ON COLUMN dashboard_snapshot.created_at IS '행 생성 시각이자 대시보드 집계 완료 시각';
COMMENT ON COLUMN dashboard_snapshot.updated_at IS '행 최종 수정 시각';
COMMENT ON COLUMN dashboard_snapshot.created_by IS '행을 생성한 시스템 사용자 ID';
COMMENT ON COLUMN dashboard_snapshot.updated_by IS '행을 최종 수정한 시스템 사용자 ID';
COMMENT ON COLUMN dashboard_snapshot.is_deleted IS '논리 삭제 여부: 0 활성, 1 삭제';
