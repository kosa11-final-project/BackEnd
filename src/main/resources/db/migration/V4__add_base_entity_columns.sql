-- Add common audit columns in a nullable state first.
-- V5 enforces NOT NULL and foreign-key constraints after existing rows are backfilled.

ALTER TABLE organization
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE sales_channel
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE warehouse
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE app_role
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE supplier
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE ingredient
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE category
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE sales_point
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE app_user
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE user_role
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE user_access_scope
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE audit_log
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE sales_point_warehouse
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE product
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE product_ingredient
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE sku
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE lot
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE ml_model_version
    ADD (
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE ml_training_run
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE demand_forecast
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE sales_daily
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE sku_channel_price
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE inventory_balance
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE inventory_policy
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE risk_assessment
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_case
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_option
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_simulation
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_action
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_lot_allocation
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_review_request
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_performance
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_inventory_snapshot
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE final_strategy_selection
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE strategy_price_snapshot
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE notification
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

ALTER TABLE inventory_movement
    ADD (
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  NUMBER,
    updated_by  NUMBER,
    is_deleted NUMBER(1) DEFAULT 0
    );

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


