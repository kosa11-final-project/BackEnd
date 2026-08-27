-- 통합 재고 조회의 SKU × 판매처 범위 탐색과 최신 이력 조회를 지원한다.
-- 이 마이그레이션은 데이터 행을 변경하지 않고 보조 인덱스만 추가한다.

CREATE INDEX ix_inv_balance_sku_scope
    ON inventory_balance (
        sku_id,
        NVL(stock_sales_point_id, NVL(allocated_sales_point_id, -1)),
        is_deleted,
        warehouse_id,
        lot_id
    );

CREATE INDEX ix_risk_balance_latest
    ON risk_assessment (
        inventory_balance_id,
        is_deleted,
        updated_at DESC,
        risk_assessment_id DESC
    );

CREATE INDEX ix_inv_policy_scope_latest
    ON inventory_policy (
        sku_id,
        COALESCE(stock_sales_point_id, allocated_sales_point_id, -1),
        is_deleted,
        effective_from DESC,
        inventory_policy_id DESC
    );

CREATE INDEX ix_sku_price_scope_latest
    ON sku_channel_price (
        sku_id,
        sales_point_id,
        is_deleted,
        effective_from DESC,
        sku_channel_price_id DESC
    );
