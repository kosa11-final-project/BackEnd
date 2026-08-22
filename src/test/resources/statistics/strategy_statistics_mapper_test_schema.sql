DROP ALL OBJECTS;

CREATE TABLE strategy_case (
    strategy_case_id NUMBER PRIMARY KEY,
    case_status VARCHAR2(30),
    is_deleted NUMBER(1)
);

CREATE TABLE strategy_option (
    strategy_option_id NUMBER PRIMARY KEY,
    strategy_case_id NUMBER,
    is_deleted NUMBER(1)
);

CREATE TABLE final_strategy_selection (
    final_selection_id NUMBER PRIMARY KEY,
    strategy_case_id NUMBER,
    strategy_option_id NUMBER,
    is_deleted NUMBER(1)
);

CREATE TABLE strategy_execution_result (
    strategy_execution_result_id NUMBER PRIMARY KEY,
    final_selection_id NUMBER,
    achievement_rate NUMBER(10,6),
    start_risk_stock_qty NUMBER(15,3),
    end_risk_stock_qty NUMBER(15,3),
    start_expected_disposal_qty NUMBER(15,3),
    end_expected_disposal_qty NUMBER(15,3),
    estimated_loss_savings_amount NUMBER(18,2),
    is_deleted NUMBER(1)
);

CREATE TABLE sales_channel (
    sales_channel_id NUMBER PRIMARY KEY,
    channel_type VARCHAR2(20),
    active_yn CHAR(1),
    is_deleted NUMBER(1)
);

CREATE TABLE sales_point (
    sales_point_id NUMBER PRIMARY KEY,
    sales_channel_id NUMBER,
    sales_point_code VARCHAR2(50),
    active_yn CHAR(1),
    is_deleted NUMBER(1)
);

CREATE TABLE warehouse (
    warehouse_id NUMBER PRIMARY KEY,
    warehouse_code VARCHAR2(50),
    active_yn CHAR(1),
    is_deleted NUMBER(1)
);

CREATE TABLE strategy_action (
    strategy_action_id NUMBER PRIMARY KEY,
    strategy_option_id NUMBER,
    action_type VARCHAR2(30),
    start_date DATE,
    end_date DATE,
    source_sales_point_id NUMBER,
    target_sales_point_id NUMBER,
    source_warehouse_id NUMBER,
    destination_warehouse_id NUMBER,
    is_deleted NUMBER(1)
);

CREATE TABLE strategy_inventory_snapshot (
    inventory_snapshot_id NUMBER PRIMARY KEY,
    strategy_case_id NUMBER,
    sales_point_id NUMBER,
    warehouse_id NUMBER,
    is_deleted NUMBER(1)
);

INSERT INTO strategy_case VALUES (101, 'EXECUTION_COMPLETED', 0);
INSERT INTO strategy_option VALUES (1001, 101, 0);
INSERT INTO final_strategy_selection VALUES (5001, 101, 1001, 0);
INSERT INTO strategy_execution_result VALUES (9001, 5001, 120, 100, 40, 30, 10, 2000, 0);

INSERT INTO sales_channel VALUES (1, 'OFFLINE', 'Y', 0);
INSERT INTO sales_point VALUES (10, 1, 'STORE-1', 'Y', 0);
INSERT INTO warehouse VALUES (20, 'WH-1', 'Y', 0);

INSERT INTO strategy_action VALUES (
    1, 1001, 'REALLOCATION', DATE '2026-08-01', DATE '2026-08-10',
    NULL, 10, 20, NULL, 0
);
INSERT INTO strategy_action VALUES (
    2, 1001, 'PRICE_DISCOUNT', DATE '2026-08-01', DATE '2026-08-10',
    NULL, 10, NULL, NULL, 0
);
INSERT INTO strategy_inventory_snapshot VALUES (1, 101, NULL, 20, 0);
