-- Oracle DDL commits statement by statement. Add only missing columns so a
-- repaired failed migration can safely resume without raising ORA-01430.

DECLARE
    TYPE table_name_list IS TABLE OF VARCHAR2(128);
    v_tables table_name_list := table_name_list(
        'organization',
        'sales_channel',
        'warehouse',
        'app_role',
        'supplier',
        'ingredient',
        'category',
        'sales_point',
        'app_user',
        'user_role',
        'user_access_scope',
        'audit_log',
        'sales_point_warehouse',
        'product',
        'product_ingredient',
        'sku',
        'lot',
        'ml_model_version',
        'ml_training_run',
        'demand_forecast',
        'sales_daily',
        'sku_channel_price',
        'inventory_balance',
        'inventory_policy',
        'risk_assessment',
        'strategy_case',
        'strategy_option',
        'strategy_simulation',
        'strategy_action',
        'strategy_lot_allocation',
        'strategy_review_request',
        'strategy_performance',
        'strategy_inventory_snapshot',
        'final_strategy_selection',
        'strategy_price_snapshot',
        'notification',
        'inventory_movement'
    );

    PROCEDURE add_column_if_missing(
        p_table_name        VARCHAR2,
        p_column_name       VARCHAR2,
        p_column_definition VARCHAR2
    ) IS
        v_column_count NUMBER;
    BEGIN
        SELECT COUNT(*)
        INTO v_column_count
        FROM user_tab_columns
        WHERE table_name = UPPER(p_table_name)
          AND column_name = UPPER(p_column_name);

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table_name
                || ' ADD (' || p_column_name || ' ' || p_column_definition || ')';
        END IF;
    END add_column_if_missing;
BEGIN
    FOR i IN 1 .. v_tables.COUNT LOOP
        add_column_if_missing(v_tables(i), 'created_at', 'TIMESTAMP');
        add_column_if_missing(v_tables(i), 'updated_at', 'TIMESTAMP');
        add_column_if_missing(v_tables(i), 'created_by', 'NUMBER');
        add_column_if_missing(v_tables(i), 'updated_by', 'NUMBER');
        add_column_if_missing(v_tables(i), 'is_deleted', 'NUMBER(1) DEFAULT 0');
    END LOOP;
END;
/

-- Create a technical actor for rows whose original creator cannot be identified.
MERGE INTO organization target
USING (
    SELECT 'SYSTEM' AS organization_code,
           'System' AS organization_name,
           'HEAD_OFFICE' AS organization_type
    FROM dual
) source
ON (target.organization_code = source.organization_code)
WHEN NOT MATCHED THEN
    INSERT (organization_code, organization_name, organization_type)
    VALUES (source.organization_code, source.organization_name, source.organization_type);

MERGE INTO app_user target
USING (
    SELECT organization_id,
           '__system__' AS login_id,
           '$2a$12$000000000000000000000uG1HiS9B56XZQ7W3sXa6iRDAe9PS6NuW' AS password_hash,
           'SYSTEM' AS user_name
    FROM organization
    WHERE organization_code = 'SYSTEM'
) source
ON (target.login_id = source.login_id)
WHEN MATCHED THEN
    UPDATE SET target.active_yn = 'N'
WHEN NOT MATCHED THEN
    INSERT (organization_id, login_id, password_hash, user_name, active_yn)
    VALUES (source.organization_id, source.login_id, source.password_hash, source.user_name, 'N');

-- Preserve existing audit values. Only missing values are backfilled.
UPDATE organization
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE sales_channel
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE warehouse
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE app_role
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE supplier
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE ingredient
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE category
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE sales_point
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE app_user
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE user_role
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE user_access_scope
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE audit_log
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, user_id),
    updated_by  = NVL(updated_by, NVL(created_by, user_id)),
    is_deleted = NVL(is_deleted, 0);

UPDATE sales_point_warehouse
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE product
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE product_ingredient
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE sku
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE lot
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE ml_model_version
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE ml_training_run
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE demand_forecast
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE sales_daily
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE sku_channel_price
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE inventory_balance
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE inventory_policy
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE risk_assessment
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_case
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_option
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_simulation
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_action
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_lot_allocation
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_review_request
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_performance
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_inventory_snapshot
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE final_strategy_selection
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE strategy_price_snapshot
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE notification
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);

UPDATE inventory_movement
SET created_at  = NVL(created_at, SYSTIMESTAMP),
    updated_at  = NVL(updated_at, NVL(created_at, SYSTIMESTAMP)),
    created_by  = NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__')),
    updated_by  = NVL(updated_by, NVL(created_by, (SELECT user_id FROM app_user WHERE login_id = '__system__'))),
    is_deleted = NVL(is_deleted, 0);
