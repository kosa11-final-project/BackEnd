DROP TABLE IF EXISTS risk_assessment;
DROP TABLE IF EXISTS inventory_balance;
DROP TABLE IF EXISTS sku_cost;
DROP TABLE IF EXISTS demand_forecast;
DROP TABLE IF EXISTS ml_model_version;
DROP TABLE IF EXISTS lot;
DROP TABLE IF EXISTS sku;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS sales_point;
DROP TABLE IF EXISTS sales_channel;
DROP TABLE IF EXISTS sales_daily;

CREATE TABLE sales_daily (
    sales_daily_id NUMBER PRIMARY KEY,
    sales_date DATE NOT NULL,
    net_sales_qty NUMBER(15,3) NOT NULL,
    is_deleted NUMBER(1) NOT NULL
);

CREATE TABLE sales_channel (
    sales_channel_id NUMBER PRIMARY KEY,
    channel_type     VARCHAR2(20) NOT NULL,
    active_yn        CHAR(1) NOT NULL,
    is_deleted       NUMBER(1) NOT NULL
);

CREATE TABLE sales_point (
    sales_point_id      NUMBER PRIMARY KEY,
    sales_point_code    VARCHAR2(50) NOT NULL,
    sales_point_name    VARCHAR2(100) NOT NULL,
    region_code         VARCHAR2(50),
    sales_channel_id    NUMBER NOT NULL,
    active_yn           CHAR(1) NOT NULL,
    is_deleted          NUMBER(1) NOT NULL
);

CREATE TABLE warehouse (
    warehouse_id    NUMBER PRIMARY KEY,
    warehouse_code  VARCHAR2(50) NOT NULL,
    warehouse_name  VARCHAR2(100) NOT NULL,
    region_code     VARCHAR2(50),
    active_yn       CHAR(1) NOT NULL,
    is_deleted      NUMBER(1) NOT NULL
);

CREATE TABLE product (
    product_id      NUMBER PRIMARY KEY,
    product_status  VARCHAR2(20) NOT NULL,
    is_deleted      NUMBER(1) NOT NULL
);

CREATE TABLE sku (
    sku_id      NUMBER PRIMARY KEY,
    product_id  NUMBER NOT NULL,
    active_yn   CHAR(1) NOT NULL,
    is_deleted  NUMBER(1) NOT NULL
);

CREATE TABLE lot (
    lot_id          NUMBER PRIMARY KEY,
    sale_stop_date  DATE,
    expiry_date     DATE,
    lot_status      VARCHAR2(20) NOT NULL,
    is_deleted      NUMBER(1) NOT NULL
);

CREATE TABLE ml_model_version (
    model_version_id NUMBER PRIMARY KEY,
    status           VARCHAR2(20) NOT NULL,
    is_deleted       NUMBER(1) NOT NULL
);

CREATE TABLE demand_forecast (
    forecast_id       NUMBER PRIMARY KEY,
    sku_id            NUMBER NOT NULL,
    sales_point_id    NUMBER NOT NULL,
    predicted_qty_d7  NUMBER NOT NULL,
    predicted_qty_d14 NUMBER NOT NULL,
    predicted_qty_d30 NUMBER NOT NULL,
    base_date         DATE NOT NULL,
    updated_at        TIMESTAMP NOT NULL,
    forecast_source   VARCHAR2(30) NOT NULL,
    model_version_id  NUMBER NOT NULL,
    is_deleted        NUMBER(1) NOT NULL
);

CREATE TABLE sku_cost (
    sku_cost_id    NUMBER PRIMARY KEY,
    sku_id         NUMBER NOT NULL,
    unit_cost      NUMBER(18,2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to   DATE,
    updated_at     TIMESTAMP NOT NULL,
    is_deleted     NUMBER(1) NOT NULL
);

CREATE TABLE inventory_balance (
    inventory_balance_id     NUMBER PRIMARY KEY,
    sku_id                   NUMBER NOT NULL,
    warehouse_id             NUMBER,
    stock_sales_point_id     NUMBER,
    allocated_sales_point_id NUMBER,
    lot_id                   NUMBER,
    on_hand_qty              NUMBER NOT NULL,
    total_qty                NUMBER NOT NULL,
    is_deleted               NUMBER(1) NOT NULL
);

CREATE TABLE risk_assessment (
    risk_assessment_id NUMBER PRIMARY KEY,
    inventory_balance_id NUMBER NOT NULL,
    forecast_id        NUMBER,
    risk_grade         VARCHAR2(20),
    shortage_yn        CHAR(1),
    updated_at         TIMESTAMP NOT NULL,
    is_deleted         NUMBER(1) NOT NULL
);

INSERT INTO sales_channel VALUES (1, 'OFFLINE', 'Y', 0);
INSERT INTO sales_point VALUES (10, 'STORE-10', '테스트 매장', 'SEOUL', 1, 'Y', 0);
INSERT INTO warehouse VALUES (101, 'WH-101', '테스트 물류센터 A', 'SEOUL', 'Y', 0);
INSERT INTO warehouse VALUES (102, 'WH-102', '테스트 물류센터 B', 'SEOUL', 'Y', 0);
INSERT INTO product VALUES (1000, 'ACTIVE', 0);
INSERT INTO sku VALUES (2000, 1000, 'Y', 0);
INSERT INTO lot VALUES (3001, DATE '2026-08-22', DATE '2026-08-25', 'AVAILABLE', 0);
INSERT INTO lot VALUES (3002, DATE '2026-08-22', DATE '2026-08-25', 'AVAILABLE', 0);
INSERT INTO ml_model_version VALUES (4000, 'ACTIVE', 0);
INSERT INTO demand_forecast VALUES (
    5000, 2000, 10, 60, 60, 60,
    DATE '2026-08-17', TIMESTAMP '2026-08-17 01:00:00', 'ML', 4000, 0
);
INSERT INTO sku_cost VALUES (
    6000, 2000, 5,
    DATE '2026-01-01', NULL, TIMESTAMP '2026-08-17 01:00:00', 0
);
INSERT INTO inventory_balance VALUES (7001, 2000, 101, NULL, 10, 3001, 50, 50, 0);
INSERT INTO inventory_balance VALUES (7002, 2000, 102, NULL, 10, 3002, 50, 50, 0);
INSERT INTO sales_daily VALUES (8001, DATE '2026-08-15', 10, 0);
INSERT INTO sales_daily VALUES (8002, DATE '2026-08-15', 20, 0);
INSERT INTO sales_daily VALUES (8003, DATE '2026-08-16', 7, 0);
INSERT INTO sales_daily VALUES (8004, DATE '2026-08-16', 99, 1);

