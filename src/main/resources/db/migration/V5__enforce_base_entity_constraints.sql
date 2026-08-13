-- Oracle DDL commits statement by statement. This migration checks the current
-- schema before each change so a repaired failed migration can safely resume.

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

    PROCEDURE enforce_column(
        p_table_name    VARCHAR2,
        p_column_name   VARCHAR2,
        p_data_type     VARCHAR2,
        p_default_value VARCHAR2 DEFAULT NULL
    ) IS
        v_nullable user_tab_columns.nullable%TYPE;
        v_sql      VARCHAR2(1000);
    BEGIN
        SELECT nullable
        INTO v_nullable
        FROM user_tab_columns
        WHERE table_name = UPPER(p_table_name)
          AND column_name = UPPER(p_column_name);

        -- Reapplying NOT NULL to an already NOT NULL column raises ORA-01442.
        -- Existing NOT NULL columns only need their default refreshed, if any.
        IF v_nullable = 'N' AND p_default_value IS NULL THEN
            RETURN;
        END IF;

        v_sql := 'ALTER TABLE ' || p_table_name
            || ' MODIFY (' || p_column_name || ' ' || p_data_type;

        IF p_default_value IS NOT NULL THEN
            v_sql := v_sql || ' DEFAULT ' || p_default_value;
        END IF;

        IF v_nullable = 'Y' THEN
            v_sql := v_sql || ' NOT NULL';
        END IF;

        v_sql := v_sql || ')';
        EXECUTE IMMEDIATE v_sql;
    END enforce_column;

    PROCEDURE add_constraint_if_missing(
        p_table_name      VARCHAR2,
        p_constraint_name VARCHAR2,
        p_definition      VARCHAR2
    ) IS
        v_constraint_count NUMBER;
    BEGIN
        SELECT COUNT(*)
        INTO v_constraint_count
        FROM user_constraints
        WHERE constraint_name = UPPER(p_constraint_name);

        IF v_constraint_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table_name
                || ' ADD CONSTRAINT ' || p_constraint_name || ' ' || p_definition;
        END IF;
    END add_constraint_if_missing;
BEGIN
    FOR i IN 1 .. v_tables.COUNT LOOP
        enforce_column(v_tables(i), 'created_at', 'TIMESTAMP', 'SYSTIMESTAMP');
        enforce_column(v_tables(i), 'updated_at', 'TIMESTAMP', 'SYSTIMESTAMP');
        enforce_column(v_tables(i), 'created_by', 'NUMBER');
        enforce_column(v_tables(i), 'updated_by', 'NUMBER');
        enforce_column(v_tables(i), 'is_deleted', 'NUMBER(1)', '0');

        add_constraint_if_missing(
            v_tables(i),
            'ck_' || v_tables(i) || '_deleted',
            'CHECK (is_deleted IN (0, 1))'
        );

        IF v_tables(i) NOT IN ('strategy_case', 'final_strategy_selection') THEN
            add_constraint_if_missing(
                v_tables(i),
                'fk_' || v_tables(i) || '_created_by',
                'FOREIGN KEY (created_by) REFERENCES app_user (user_id)'
            );
        END IF;

        add_constraint_if_missing(
            v_tables(i),
            'fk_' || v_tables(i) || '_updated_by',
            'FOREIGN KEY (updated_by) REFERENCES app_user (user_id)'
        );
    END LOOP;
END;
/

COMMENT ON COLUMN organization.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN organization.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN organization.created_by IS 'User ID that created the row';
COMMENT ON COLUMN organization.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN organization.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN sales_channel.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN sales_channel.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN sales_channel.created_by IS 'User ID that created the row';
COMMENT ON COLUMN sales_channel.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN sales_channel.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN warehouse.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN warehouse.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN warehouse.created_by IS 'User ID that created the row';
COMMENT ON COLUMN warehouse.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN warehouse.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN app_role.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN app_role.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN app_role.created_by IS 'User ID that created the row';
COMMENT ON COLUMN app_role.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN app_role.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN supplier.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN supplier.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN supplier.created_by IS 'User ID that created the row';
COMMENT ON COLUMN supplier.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN supplier.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN ingredient.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN ingredient.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN ingredient.created_by IS 'User ID that created the row';
COMMENT ON COLUMN ingredient.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN ingredient.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN category.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN category.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN category.created_by IS 'User ID that created the row';
COMMENT ON COLUMN category.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN category.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN sales_point.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN sales_point.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN sales_point.created_by IS 'User ID that created the row';
COMMENT ON COLUMN sales_point.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN sales_point.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN app_user.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN app_user.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN app_user.created_by IS 'User ID that created the row';
COMMENT ON COLUMN app_user.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN app_user.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN user_role.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN user_role.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN user_role.created_by IS 'User ID that created the row';
COMMENT ON COLUMN user_role.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN user_role.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN user_access_scope.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN user_access_scope.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN user_access_scope.created_by IS 'User ID that created the row';
COMMENT ON COLUMN user_access_scope.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN user_access_scope.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN audit_log.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN audit_log.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN audit_log.created_by IS 'User ID that created the row';
COMMENT ON COLUMN audit_log.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN audit_log.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN sales_point_warehouse.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN sales_point_warehouse.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN sales_point_warehouse.created_by IS 'User ID that created the row';
COMMENT ON COLUMN sales_point_warehouse.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN sales_point_warehouse.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN product.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN product.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN product.created_by IS 'User ID that created the row';
COMMENT ON COLUMN product.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN product.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN product_ingredient.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN product_ingredient.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN product_ingredient.created_by IS 'User ID that created the row';
COMMENT ON COLUMN product_ingredient.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN product_ingredient.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN sku.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN sku.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN sku.created_by IS 'User ID that created the row';
COMMENT ON COLUMN sku.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN sku.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN lot.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN lot.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN lot.created_by IS 'User ID that created the row';
COMMENT ON COLUMN lot.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN lot.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN ml_model_version.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN ml_model_version.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN ml_model_version.created_by IS 'User ID that created the row';
COMMENT ON COLUMN ml_model_version.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN ml_model_version.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN ml_training_run.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN ml_training_run.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN ml_training_run.created_by IS 'User ID that created the row';
COMMENT ON COLUMN ml_training_run.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN ml_training_run.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN demand_forecast.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN demand_forecast.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN demand_forecast.created_by IS 'User ID that created the row';
COMMENT ON COLUMN demand_forecast.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN demand_forecast.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN sales_daily.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN sales_daily.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN sales_daily.created_by IS 'User ID that created the row';
COMMENT ON COLUMN sales_daily.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN sales_daily.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN sku_channel_price.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN sku_channel_price.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN sku_channel_price.created_by IS 'User ID that created the row';
COMMENT ON COLUMN sku_channel_price.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN sku_channel_price.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN inventory_balance.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN inventory_balance.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN inventory_balance.created_by IS 'User ID that created the row';
COMMENT ON COLUMN inventory_balance.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN inventory_balance.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN inventory_policy.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN inventory_policy.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN inventory_policy.created_by IS 'User ID that created the row';
COMMENT ON COLUMN inventory_policy.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN inventory_policy.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN risk_assessment.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN risk_assessment.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN risk_assessment.created_by IS 'User ID that created the row';
COMMENT ON COLUMN risk_assessment.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN risk_assessment.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_case.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_case.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_case.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_case.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_case.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_option.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_option.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_option.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_option.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_option.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_simulation.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_simulation.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_simulation.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_simulation.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_simulation.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_action.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_action.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_action.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_action.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_action.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_lot_allocation.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_lot_allocation.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_lot_allocation.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_lot_allocation.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_lot_allocation.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_review_request.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_review_request.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_review_request.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_review_request.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_review_request.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_performance.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_performance.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_performance.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_performance.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_performance.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_inventory_snapshot.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_inventory_snapshot.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_inventory_snapshot.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_inventory_snapshot.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_inventory_snapshot.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN final_strategy_selection.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN final_strategy_selection.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN final_strategy_selection.created_by IS 'User ID that created the row';
COMMENT ON COLUMN final_strategy_selection.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN final_strategy_selection.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN strategy_price_snapshot.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN strategy_price_snapshot.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN strategy_price_snapshot.created_by IS 'User ID that created the row';
COMMENT ON COLUMN strategy_price_snapshot.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN strategy_price_snapshot.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN notification.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN notification.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN notification.created_by IS 'User ID that created the row';
COMMENT ON COLUMN notification.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN notification.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';

COMMENT ON COLUMN inventory_movement.created_at IS 'Row creation timestamp';
COMMENT ON COLUMN inventory_movement.updated_at IS 'Row last update timestamp';
COMMENT ON COLUMN inventory_movement.created_by IS 'User ID that created the row';
COMMENT ON COLUMN inventory_movement.updated_by IS 'User ID that last updated the row';
COMMENT ON COLUMN inventory_movement.is_deleted IS 'Soft delete flag: 0 active, 1 deleted';


