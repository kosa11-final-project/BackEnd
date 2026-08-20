DROP TABLE IF EXISTS risk_assessment;
DROP TABLE IF EXISTS inventory_balance;
DROP TABLE IF EXISTS demand_forecast;
DROP TABLE IF EXISTS ml_model_version;
DROP TABLE IF EXISTS lot;
DROP TABLE IF EXISTS sku;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS sales_point;
DROP TABLE IF EXISTS sales_channel;

CREATE TABLE sales_channel (
    sales_channel_id NUMBER PRIMARY KEY,
    channel_type     VARCHAR2(20) NOT NULL,
    active_yn        CHAR(1) NOT NULL,
    is_deleted       NUMBER(1) NOT NULL
);

CREATE TABLE sales_point (
    sales_point_id   NUMBER PRIMARY KEY,
    sales_point_code VARCHAR2(50) NOT NULL,
    sales_point_name VARCHAR2(100) NOT NULL,
    region_code      VARCHAR2(50),
    address          VARCHAR2(300),
    sales_channel_id NUMBER NOT NULL,
    active_yn        CHAR(1) NOT NULL,
    is_deleted       NUMBER(1) NOT NULL
);

CREATE TABLE warehouse (
    warehouse_id   NUMBER PRIMARY KEY,
    warehouse_code VARCHAR2(50) NOT NULL,
    warehouse_name VARCHAR2(100) NOT NULL,
    region_code    VARCHAR2(50),
    address        VARCHAR2(300),
    active_yn      CHAR(1) NOT NULL,
    is_deleted     NUMBER(1) NOT NULL
);

CREATE TABLE product (
    product_id        NUMBER PRIMARY KEY,
    product_status    VARCHAR2(20) NOT NULL,
    sale_available_yn CHAR(1) NOT NULL,
    is_deleted        NUMBER(1) NOT NULL
);

CREATE TABLE sku (
    sku_id     NUMBER PRIMARY KEY,
    product_id NUMBER NOT NULL,
    sku_code   VARCHAR2(50) NOT NULL,
    sku_name   VARCHAR2(100) NOT NULL,
    active_yn  CHAR(1) NOT NULL,
    is_deleted NUMBER(1) NOT NULL
);

CREATE TABLE lot (
    lot_id         NUMBER PRIMARY KEY,
    sale_stop_date DATE,
    expiry_date    DATE,
    lot_status     VARCHAR2(20) NOT NULL,
    is_deleted     NUMBER(1) NOT NULL
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

CREATE TABLE inventory_balance (
    inventory_balance_id     NUMBER PRIMARY KEY,
    sku_id                   NUMBER NOT NULL,
    warehouse_id             NUMBER,
    stock_sales_point_id     NUMBER,
    allocated_sales_point_id NUMBER,
    lot_id                   NUMBER,
    on_hand_qty              NUMBER NOT NULL,
    reserved_qty             NUMBER NOT NULL,
    total_qty                NUMBER NOT NULL,
    is_deleted               NUMBER(1) NOT NULL
);

CREATE TABLE risk_assessment (
    risk_assessment_id  NUMBER PRIMARY KEY,
    inventory_balance_id NUMBER NOT NULL,
    forecast_id         NUMBER,
    risk_grade          VARCHAR2(20),
    shortage_yn         CHAR(1),
    risk_score          NUMBER,
    reason_message      VARCHAR2(1000),
    updated_at          TIMESTAMP NOT NULL,
    is_deleted          NUMBER(1) NOT NULL
);

INSERT INTO sales_channel VALUES (1, 'ONLINE', 'Y', 0);
INSERT INTO sales_channel VALUES (2, 'OFFLINE', 'Y', 0);

INSERT INTO sales_point VALUES (10, 'GREETING', '그리팅몰', 'ONLINE', NULL, 1, 'Y', 0);
INSERT INTO sales_point VALUES (11, 'MODU_MATJIP', '모두의 맛집', 'ONLINE', NULL, 1, 'Y', 0);
INSERT INTO sales_point VALUES (20, 'STORE-1', '테스트 매장', 'SEOUL', '서울특별시', 2, 'Y', 0);

INSERT INTO warehouse VALUES (100, 'GYEONGIN_1', '테스트 물류센터', 'SEOUL', '서울특별시', 'Y', 0);
INSERT INTO warehouse VALUES (101, 'GYEONGIN_2', '테스트 물류센터 2', 'SEOUL', '서울특별시', 'Y', 0);
INSERT INTO product VALUES (1000, 'ACTIVE', 'Y', 0);
INSERT INTO sku VALUES (2000, 1000, 'SKU-1', '테스트 SKU', 'Y', 0);
INSERT INTO lot VALUES (3000, DATE '2026-09-10', DATE '2026-09-15', 'AVAILABLE', 0);
INSERT INTO ml_model_version VALUES (4000, 'ACTIVE', 0);

INSERT INTO demand_forecast VALUES (
    5000, 2000, 10, 30, 30, 30,
    DATE '2026-08-20', TIMESTAMP '2026-08-20 01:00:00', 'LIGHTGBM', 4000, 0
);
INSERT INTO demand_forecast VALUES (
    5001, 2000, 20, 100, 100, 100,
    DATE '2026-08-20', TIMESTAMP '2026-08-20 01:00:00', 'LIGHTGBM', 4000, 0
);

-- 판매처가 정해지지 않은 물류센터 재고
INSERT INTO inventory_balance VALUES (6000, 2000, 100, NULL, NULL, 3000, 10, 0, 10, 0);
-- 운영 목표 형태: 물류센터에 보관되며 온라인 판매처에만 할당된 재고
INSERT INTO inventory_balance VALUES (6001, 2000, 100, NULL, 10, 3000, 20, 0, 20, 0);
-- 현재 적재 데이터 호환 형태: 보관·할당 판매처가 모두 온라인인 재고
INSERT INTO inventory_balance VALUES (6002, 2000, 101, 10, 10, 3000, 30, 0, 30, 0);
-- 오프라인 매장에 보관된 재고
INSERT INTO inventory_balance VALUES (6003, 2000, 100, 20, 20, 3000, 40, 0, 40, 0);

INSERT INTO risk_assessment VALUES (
    7000, 6001, 5000, 'CRITICAL', 'N', 90, '온라인 재고 위험',
    TIMESTAMP '2026-08-20 02:00:00', 0
);
