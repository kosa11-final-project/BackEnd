DROP TABLE IF EXISTS inventory_policy;
DROP TABLE IF EXISTS sku_cost;
DROP TABLE IF EXISTS sku_channel_price;
DROP TABLE IF EXISTS inventory_balance;
DROP TABLE IF EXISTS lot;
DROP TABLE IF EXISTS sales_point;
DROP TABLE IF EXISTS sku;

CREATE TABLE sku (
    sku_id NUMBER PRIMARY KEY,
    sku_code VARCHAR2(50) NOT NULL,
    sku_name VARCHAR2(200) NOT NULL,
    unit_code VARCHAR2(20) NOT NULL,
    package_quantity NUMBER(12,3) NOT NULL,
    active_yn CHAR(1) NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE sales_point (
    sales_point_id NUMBER PRIMARY KEY,
    sales_point_code VARCHAR2(50) NOT NULL,
    sales_point_name VARCHAR2(200) NOT NULL,
    active_yn CHAR(1) NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE lot (
    lot_id NUMBER PRIMARY KEY,
    manufactured_date DATE,
    received_date DATE,
    expiry_date DATE,
    sale_stop_date DATE,
    lot_status VARCHAR2(30) NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE inventory_balance (
    inventory_balance_id NUMBER PRIMARY KEY,
    sku_id NUMBER NOT NULL,
    warehouse_id NUMBER,
    stock_sales_point_id NUMBER,
    allocated_sales_point_id NUMBER,
    lot_id NUMBER,
    on_hand_qty NUMBER(15,3) NOT NULL,
    reserved_qty NUMBER(15,3) NOT NULL,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE sku_channel_price (
    sku_channel_price_id NUMBER PRIMARY KEY,
    sku_id NUMBER NOT NULL,
    sales_point_id NUMBER NOT NULL,
    selling_price NUMBER(18,2) NOT NULL,
    minimum_selling_price NUMBER(18,2),
    actual_price NUMBER(18,2) NOT NULL,
    payment_fee NUMBER(18,2),
    logistics_cost NUMBER(18,2),
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE sku_cost (
    sku_cost_id NUMBER PRIMARY KEY,
    sku_id NUMBER NOT NULL,
    unit_cost NUMBER(18,2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

CREATE TABLE inventory_policy (
    inventory_policy_id NUMBER PRIMARY KEY,
    sku_id NUMBER NOT NULL,
    warehouse_id NUMBER,
    stock_sales_point_id NUMBER,
    allocated_sales_point_id NUMBER,
    safety_stock_qty NUMBER(15,3) NOT NULL,
    target_stock_qty NUMBER(15,3),
    effective_from DATE,
    effective_to DATE,
    daily_unit_holding_cost NUMBER(18,4),
    unit_disposal_cost NUMBER(18,2),
    is_deleted NUMBER(1) DEFAULT 0 NOT NULL
);

INSERT INTO sku VALUES (101, 'SKU-101', '테스트 SKU', 'EA', 1, 'Y', 0);
INSERT INTO sales_point VALUES (10, 'SP-10', '판매처 10', 'Y', 0);
INSERT INTO sales_point VALUES (20, 'SP-20', '판매처 20', 'Y', 0);
INSERT INTO sales_point VALUES (30, 'SP-30', '판매처 30', 'Y', 0);
INSERT INTO sales_point VALUES (40, 'SP-40', '판매처 40', 'Y', 0);
INSERT INTO lot VALUES (
    1001,
    DATE '2026-07-30',
    DATE '2026-08-01',
    DATE '2026-09-01',
    NULL,
    'AVAILABLE',
    0
);
INSERT INTO inventory_balance VALUES (
    1, 101, 501, 10, 10, 1001, 20, 3, 0
);
INSERT INTO sku_channel_price VALUES (
    100, 101, 10, 12000, 7000, 10000, 300, 500,
    DATE '2026-08-01', NULL, 0
);
INSERT INTO sku_channel_price VALUES (
    200, 101, 20, 13000, 8000, 11000, 330, 550,
    DATE '2026-08-01', DATE '2026-08-19', 0
);
INSERT INTO sku_channel_price VALUES (
    300, 101, 30, 13000, 8000, 11000, 330, 550,
    TIMESTAMP '2026-08-20 00:00:00', TIMESTAMP '2026-08-31 23:59:59', 0
);
INSERT INTO sku_channel_price VALUES (
    400, 101, 40, 13000, 8000, 11000, 330, 550,
    TIMESTAMP '2026-08-01 00:00:00', TIMESTAMP '2026-08-20 00:00:00', 0
);
INSERT INTO sku_cost VALUES (
    1, 101, 6000, DATE '2026-08-01', NULL, 0
);
INSERT INTO inventory_policy VALUES (
    1, 101, 501, 10, 10, 5, 30,
    DATE '2026-08-01', NULL, 2.5, 1000, 0
);
