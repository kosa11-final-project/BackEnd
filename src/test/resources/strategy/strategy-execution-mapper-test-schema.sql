DROP ALL OBJECTS;

CREATE TABLE product (
    product_id NUMBER PRIMARY KEY, product_name VARCHAR2(200), image_url VARCHAR2(1000), is_deleted NUMBER(1)
);
CREATE TABLE sku (
    sku_id NUMBER PRIMARY KEY, product_id NUMBER, sku_code VARCHAR2(50), sku_name VARCHAR2(200),
    unit_code VARCHAR2(20), is_deleted NUMBER(1)
);
CREATE TABLE strategy_case (
    strategy_case_id NUMBER PRIMARY KEY, sku_id NUMBER, case_code VARCHAR2(50), case_name VARCHAR2(200),
    case_status VARCHAR2(30), is_deleted NUMBER(1)
);
CREATE TABLE strategy_option (
    strategy_option_id NUMBER PRIMARY KEY, strategy_case_id NUMBER, option_name VARCHAR2(200),
    recommendation_reason VARCHAR2(2000), is_deleted NUMBER(1)
);
CREATE TABLE final_strategy_selection (
    final_selection_id NUMBER PRIMARY KEY, strategy_case_id NUMBER, strategy_option_id NUMBER,
    created_at TIMESTAMP, last_synced_at TIMESTAMP, is_deleted NUMBER(1)
);
CREATE TABLE sales_point (
    sales_point_id NUMBER PRIMARY KEY, sales_point_code VARCHAR2(50), sales_point_name VARCHAR2(200),
    is_deleted NUMBER(1)
);
CREATE TABLE warehouse (
    warehouse_id NUMBER PRIMARY KEY, warehouse_code VARCHAR2(50), warehouse_name VARCHAR2(200),
    is_deleted NUMBER(1)
);
CREATE TABLE strategy_action (
    strategy_action_id NUMBER PRIMARY KEY, strategy_option_id NUMBER, action_type VARCHAR2(30),
    action_quantity NUMBER(15,3), action_order NUMBER(5), start_date DATE, end_date DATE,
    source_sales_point_id NUMBER, target_sales_point_id NUMBER, source_warehouse_id NUMBER,
    destination_warehouse_id NUMBER, is_deleted NUMBER(1)
);
CREATE TABLE inventory_balance (
    inventory_balance_id NUMBER PRIMARY KEY, on_hand_qty NUMBER(15,3), is_deleted NUMBER(1)
);
CREATE TABLE strategy_inventory_snapshot (
    inventory_snapshot_id NUMBER PRIMARY KEY, strategy_case_id NUMBER, sales_point_id NUMBER,
    warehouse_id NUMBER, inventory_balance_id NUMBER, on_hand_qty NUMBER(15,3),
    safety_stock_qty NUMBER(15,3), is_deleted NUMBER(1)
);
CREATE TABLE sales_daily (
    sales_daily_id NUMBER PRIMARY KEY, sku_id NUMBER, sales_point_id NUMBER, sales_date DATE,
    net_sales_qty NUMBER(15,3), net_sales_amount NUMBER(18,2), is_deleted NUMBER(1)
);
CREATE TABLE strategy_performance (
    strategy_performance_id NUMBER PRIMARY KEY, strategy_option_id NUMBER, performance_date DATE,
    actual_sales_qty NUMBER(15,3), actual_revenue NUMBER(18,2), actual_contribution_margin NUMBER(18,2),
    actual_remaining_qty NUMBER(15,3), moved_quantity NUMBER(15,3), disposed_quantity NUMBER(15,3),
    is_deleted NUMBER(1)
);

INSERT INTO product VALUES (1, '테스트 상품', 'https://example.com/image.jpg', 0);
INSERT INTO sku VALUES (1, 1, 'SKU-1', '테스트 SKU', '개', 0);
INSERT INTO strategy_case VALUES (101, 1, 'SC-101', '테스트 전략', 'EXECUTING', 0);
INSERT INTO strategy_option VALUES (1000, 101, '이전 선택 전략', '이전 선택', 0);
INSERT INTO strategy_option VALUES (1001, 101, '재할당 전략', '재고 편중 완화', 0);
INSERT INTO final_strategy_selection VALUES (
    5000, 101, 1000, TIMESTAMP '2026-04-01 10:00:00', NULL, 0
);
INSERT INTO final_strategy_selection VALUES (
    5001, 101, 1001, TIMESTAMP '2026-05-01 10:00:00', TIMESTAMP '2026-05-03 12:00:00', 0
);
INSERT INTO sales_point VALUES (10, 'GREETING', '그리팅몰', 0);
INSERT INTO warehouse VALUES (501, 'SEONGNAM', '성남센터', 0);
INSERT INTO strategy_action VALUES (
    11, 1001, 'REALLOCATION', 20, 1, DATE '2026-05-01', DATE '2026-05-10',
    NULL, 10, 501, NULL, 0
);
INSERT INTO strategy_action VALUES (
    12, 1001, 'PRICE_DISCOUNT', 10, 2, DATE '2026-05-01', DATE '2026-05-10',
    NULL, 10, NULL, NULL, 0
);
INSERT INTO inventory_balance VALUES (9001, 80, 0);
INSERT INTO strategy_inventory_snapshot VALUES (8001, 101, NULL, 501, 9001, 100, 30, 0);
INSERT INTO sales_daily VALUES (7001, 1, 10, DATE '2026-05-02', 7, 70000, 0);
INSERT INTO sales_daily VALUES (7002, 1, 10, DATE '2026-07-29', 3, 30000, 0);
INSERT INTO sales_daily VALUES (7003, 1, 10, DATE '2026-07-30', 99, 990000, 0);
INSERT INTO strategy_performance VALUES (6001, 1001, DATE '2026-05-02', 7, 70000, 30000, 93, 10, 0, 0);
INSERT INTO strategy_performance VALUES (6002, 1001, DATE '2026-05-03', 5, 50000, 20000, 88, 10, 0, 0);
