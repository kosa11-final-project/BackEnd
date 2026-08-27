DROP ALL OBJECTS;

CREATE TABLE category (
    category_id NUMBER PRIMARY KEY,
    category_name VARCHAR2(200),
    category_level NUMBER(2)
);
CREATE TABLE product (
    product_id NUMBER PRIMARY KEY,
    category_id NUMBER,
    image_url VARCHAR2(1000)
);
CREATE TABLE sku (
    sku_id NUMBER PRIMARY KEY,
    product_id NUMBER,
    sku_code VARCHAR2(50),
    sku_name VARCHAR2(200)
);
CREATE TABLE app_user (
    user_id NUMBER PRIMARY KEY,
    user_name VARCHAR2(100)
);
CREATE TABLE sales_point (
    sales_point_id NUMBER PRIMARY KEY,
    sales_point_code VARCHAR2(50),
    sales_point_name VARCHAR2(200)
);
CREATE TABLE warehouse (
    warehouse_id NUMBER PRIMARY KEY,
    warehouse_code VARCHAR2(50),
    warehouse_name VARCHAR2(200)
);
CREATE TABLE lot (
    lot_id NUMBER PRIMARY KEY,
    lot_no VARCHAR2(100)
);
CREATE TABLE strategy_case (
    strategy_case_id NUMBER PRIMARY KEY,
    sku_id NUMBER,
    requested_sales_point_id NUMBER,
    case_code VARCHAR2(100),
    case_name VARCHAR2(200),
    case_status VARCHAR2(30),
    generation_stage VARCHAR2(30),
    recommendation_outcome VARCHAR2(30),
    request_payload_json CLOB,
    result_cache_key VARCHAR2(500),
    result_expires_at TIMESTAMP,
    failure_code VARCHAR2(100),
    failure_message VARCHAR2(2000),
    completed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by NUMBER,
    updated_by NUMBER,
    is_deleted NUMBER(1)
);

INSERT INTO category VALUES (301, '국·탕', 3);
INSERT INTO product VALUES (401, 301, 'https://example.com/mushroom-soup.jpg');
INSERT INTO sku VALUES (6032, 401, 'GF-SOUP-MSH-06', '버섯 들깨탕 6팩');
INSERT INTO app_user VALUES (7, '김영만');
INSERT INTO sales_point VALUES (10, 'DEPT_MOKDONG', '목동점');
INSERT INTO sales_point VALUES (20, 'DEPT_PANGYO', '판교점');
INSERT INTO warehouse VALUES (500, 'WH_GYEONGIN', '경인센터');
INSERT INTO warehouse VALUES (600, 'WH_SUJI', '수지센터');
INSERT INTO lot VALUES (501, 'LOT-260801-A');
INSERT INTO lot VALUES (502, 'LOT-260802-B');

INSERT INTO strategy_case VALUES (
    123, 6032, 10, 'SC-123', '버섯 들깨탕 수도권 재배치 전략',
    'GENERATED', 'COMPARISON_READY', 'OPTIONS_GENERATED',
    '{"lotIds":[501],"candidateSalesPointIds":[20],"strategyTypes":["RT_TRANSFER"],"forecastStartDate":"2026-08-20","forecastEndDate":"2026-08-27"}',
    'ai-strategy:case:123:result:v1', TIMESTAMP '2026-08-27 10:00:00',
    NULL, NULL, TIMESTAMP '2026-08-24 10:01:00',
    TIMESTAMP '2026-08-24 10:00:00', TIMESTAMP '2026-08-24 10:01:00',
    7, 7, 0
);
