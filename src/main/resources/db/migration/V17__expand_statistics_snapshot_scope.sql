ALTER TABLE statistics_snapshot
    DROP CONSTRAINT ck_statistics_scope_reference;

ALTER TABLE statistics_snapshot
    ADD CONSTRAINT ck_statistics_scope_reference
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
        );

COMMENT ON COLUMN statistics_snapshot.warehouse_id
    IS '개별 WAREHOUSE 범위가 참조하는 물류센터 ID. 범위 전체 집계는 NULL';
COMMENT ON COLUMN statistics_snapshot.sales_point_id
    IS '개별 OFFLINE_STORE 또는 ONLINE_STORE 범위가 참조하는 판매처 ID. 범위 전체 집계는 NULL';
COMMENT ON COLUMN statistics_snapshot.scope_code
    IS '통계 범위 코드. 범위 전체 집계는 ALL, 미할당은 UNASSIGNED, 개별 위치는 물류센터 또는 판매처 코드';
