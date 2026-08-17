-- Validate legacy data before applying Oracle DDL, because each DDL statement
-- commits independently and a late validation failure would leave a partial migration.
DECLARE
    v_obsolete_action_count NUMBER;
    v_missing_source_count  NUMBER;
    v_multiple_point_count  NUMBER;
    v_multiple_warehouse_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_obsolete_action_count
    FROM strategy_action
    WHERE action_type IN ('COUPON', 'POINT_REWARD', 'FREE_SHIPPING');

    IF v_obsolete_action_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20017,
            'V17 blocked: strategy_action contains removed action types'
        );
    END IF;

    SELECT COUNT(*)
    INTO v_missing_source_count
    FROM strategy_action action
    WHERE action.action_type IN ('REALLOCATION', 'RT_TRANSFER')
      AND action.source_warehouse_id IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM strategy_lot_allocation allocation
          WHERE allocation.strategy_action_id = action.strategy_action_id
            AND (
                allocation.source_sales_point_id IS NOT NULL
                OR allocation.source_warehouse_id IS NOT NULL
            )
      );

    IF v_missing_source_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20018,
            'V17 blocked: movement actions without a source location exist'
        );
    END IF;

    SELECT COUNT(*)
    INTO v_multiple_point_count
    FROM (
        SELECT action.strategy_action_id
        FROM strategy_action action
        JOIN strategy_lot_allocation allocation
          ON allocation.strategy_action_id = action.strategy_action_id
        WHERE action.action_type IN ('REALLOCATION', 'RT_TRANSFER')
          AND action.source_warehouse_id IS NULL
          AND allocation.source_sales_point_id IS NOT NULL
        GROUP BY action.strategy_action_id
        HAVING COUNT(DISTINCT allocation.source_sales_point_id) > 1
    );

    IF v_multiple_point_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20019,
            'V17 blocked: movement actions with multiple source sales points exist'
        );
    END IF;

    SELECT COUNT(*)
    INTO v_multiple_warehouse_count
    FROM (
        SELECT action.strategy_action_id
        FROM strategy_action action
        JOIN strategy_lot_allocation allocation
          ON allocation.strategy_action_id = action.strategy_action_id
        WHERE action.action_type IN ('REALLOCATION', 'RT_TRANSFER')
          AND action.source_warehouse_id IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM strategy_lot_allocation point_allocation
              WHERE point_allocation.strategy_action_id = action.strategy_action_id
                AND point_allocation.source_sales_point_id IS NOT NULL
          )
          AND allocation.source_warehouse_id IS NOT NULL
        GROUP BY action.strategy_action_id
        HAVING COUNT(DISTINCT allocation.source_warehouse_id) > 1
    );

    IF v_multiple_warehouse_count > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20020,
            'V17 blocked: movement actions with multiple source warehouses exist'
        );
    END IF;
END;
/

ALTER TABLE strategy_case ADD (
    case_name            VARCHAR2(200),
    request_payload_json CLOB,
    result_cache_key     VARCHAR2(500),
    result_expires_at    TIMESTAMP,
    failure_code         VARCHAR2(100),
    failure_message      VARCHAR2(2000),
    completed_at         TIMESTAMP
);

UPDATE strategy_case
SET case_name = case_code,
    request_payload_json = '{}';

ALTER TABLE strategy_case MODIFY (
    case_name            NOT NULL,
    request_payload_json NOT NULL
);

ALTER TABLE strategy_case
    ADD CONSTRAINT ck_strat_case_req_json
        CHECK (request_payload_json IS JSON);

ALTER TABLE strategy_case
    DROP CONSTRAINT ck_strategy_case_objective;

ALTER TABLE strategy_case
    DROP COLUMN primary_objective;

ALTER TABLE strategy_case
    DROP CONSTRAINT ck_strategy_case_status;

ALTER TABLE strategy_case
    ADD CONSTRAINT ck_strategy_case_status
        CHECK (
            case_status IN (
                'GENERATING',
                'GENERATED',
                'GENERATION_FAILED',
                'READY_TO_EXECUTE',
                'EXECUTING',
                'EXECUTION_COMPLETED',
                'EXPIRED'
            )
        );

ALTER TABLE strategy_action
    DROP CONSTRAINT fk_strategy_action_point;

ALTER TABLE strategy_action
    RENAME COLUMN sales_point_id TO target_sales_point_id;

ALTER TABLE strategy_action MODIFY (
    target_sales_point_id NULL
);

ALTER TABLE strategy_action ADD (
    source_sales_point_id NUMBER
);

UPDATE strategy_action action
SET source_sales_point_id = (
    SELECT MIN(allocation.source_sales_point_id)
    FROM strategy_lot_allocation allocation
    WHERE allocation.strategy_action_id = action.strategy_action_id
      AND allocation.source_sales_point_id IS NOT NULL
)
WHERE action.action_type IN ('REALLOCATION', 'RT_TRANSFER')
  AND action.source_warehouse_id IS NULL;

UPDATE strategy_action action
SET source_warehouse_id = (
    SELECT MIN(allocation.source_warehouse_id)
    FROM strategy_lot_allocation allocation
    WHERE allocation.strategy_action_id = action.strategy_action_id
      AND allocation.source_warehouse_id IS NOT NULL
)
WHERE action.action_type IN ('REALLOCATION', 'RT_TRANSFER')
  AND action.source_warehouse_id IS NULL
  AND action.source_sales_point_id IS NULL;

ALTER TABLE strategy_action
    ADD CONSTRAINT fk_strat_action_source_point
        FOREIGN KEY (source_sales_point_id)
            REFERENCES sales_point (sales_point_id);

ALTER TABLE strategy_action
    ADD CONSTRAINT fk_strat_action_target_point
        FOREIGN KEY (target_sales_point_id)
            REFERENCES sales_point (sales_point_id);

ALTER TABLE strategy_action
    ADD CONSTRAINT ck_strat_action_target_loc
        CHECK (
            target_sales_point_id IS NOT NULL
            OR destination_warehouse_id IS NOT NULL
        );

ALTER TABLE strategy_action
    ADD CONSTRAINT ck_strat_action_source_loc
        CHECK (
            action_type NOT IN ('REALLOCATION', 'RT_TRANSFER')
            OR source_sales_point_id IS NOT NULL
            OR source_warehouse_id IS NOT NULL
        );

ALTER TABLE strategy_action
    DROP CONSTRAINT ck_strategy_action_type;

ALTER TABLE strategy_action
    ADD CONSTRAINT ck_strategy_action_type
        CHECK (
            action_type IN (
                'REALLOCATION',
                'RT_TRANSFER',
                'PRICE_DISCOUNT',
                'PROMOTION_STOP',
                'CHANNEL_EXPANSION',
                'CHANNEL_CONCENTRATION',
                'REPLENISHMENT_REQUEST',
                'SAFETY_STOCK_ADJUSTMENT'
            )
        );

ALTER TABLE strategy_simulation ADD (
    expected_sell_through_days NUMBER(5)
);

ALTER TABLE strategy_simulation
    ADD CONSTRAINT ck_strat_sim_sell_days
        CHECK (
            expected_sell_through_days IS NULL
            OR expected_sell_through_days >= 0
        );

ALTER TABLE strategy_inventory_snapshot MODIFY (
    sales_point_id NULL
);

COMMENT ON COLUMN strategy_case.case_name
    IS '사용자 입력 또는 규칙 기반으로 생성된 전략 요청명';

COMMENT ON COLUMN strategy_case.request_payload_json
    IS 'LOT, 후보 판매처, 전략 타입 우선순위 및 희망 기간을 포함한 요청 JSON';

COMMENT ON COLUMN strategy_case.result_cache_key
    IS 'Redis에 저장된 전략 생성 결과의 캐시 키';

COMMENT ON COLUMN strategy_case.result_expires_at
    IS 'Redis 전략 생성 결과 만료 예정일시';

COMMENT ON COLUMN strategy_case.failure_code
    IS '전략 생성 실패 코드';

COMMENT ON COLUMN strategy_case.failure_message
    IS '전략 생성 실패 사유';

COMMENT ON COLUMN strategy_case.completed_at
    IS '전략 생성 완료일시';

COMMENT ON COLUMN strategy_case.requested_sales_point_id
    IS '전략 생성 요청의 현재 또는 출발 판매처 ID. 공용 미할당 재고는 NULL';

COMMENT ON COLUMN strategy_case.case_status
    IS '전략 상태: GENERATING, GENERATED, GENERATION_FAILED, READY_TO_EXECUTE, EXECUTING, EXECUTION_COMPLETED, EXPIRED';

COMMENT ON COLUMN strategy_action.source_sales_point_id
    IS '재고 재할당 또는 이동의 출발 판매처 ID';

COMMENT ON COLUMN strategy_action.target_sales_point_id
    IS '전략 적용 또는 재고 이동의 대상 판매처 ID';

COMMENT ON COLUMN strategy_action.action_type
    IS '전략 액션 타입';

COMMENT ON COLUMN strategy_simulation.expected_sell_through_days
    IS '전략 적용 수량의 예상 소진 일수. 기간 내 미소진은 NULL';

COMMENT ON COLUMN strategy_inventory_snapshot.sales_point_id
    IS '재고 보유 판매처 ID. 공용 미할당 재고는 NULL';
