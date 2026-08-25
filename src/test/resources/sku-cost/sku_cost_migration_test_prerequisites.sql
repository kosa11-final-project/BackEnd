CREATE TABLE app_user (
    user_id     NUMBER        NOT NULL,
    login_id   VARCHAR2(100) NOT NULL,
    is_deleted NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (user_id)
);

CREATE TABLE sku (
    sku_id NUMBER NOT NULL,
    CONSTRAINT pk_sku PRIMARY KEY (sku_id)
);

CREATE TABLE sku_channel_price (
    sku_channel_price_id NUMBER        NOT NULL,
    sku_id               NUMBER        NOT NULL,
    sales_point_id       NUMBER        NOT NULL,
    product_cost         NUMBER(18,2)  NOT NULL,
    effective_from       DATE          NOT NULL,
    effective_to         DATE,
    updated_at           TIMESTAMP     NOT NULL,
    is_deleted           NUMBER(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_sku_channel_price PRIMARY KEY (sku_channel_price_id)
);

INSERT INTO app_user (user_id, login_id, is_deleted)
VALUES (1, '__system__', 0);

INSERT INTO sku (sku_id) VALUES (101);
INSERT INTO sku (sku_id) VALUES (102);
INSERT INTO sku (sku_id) VALUES (103);
INSERT INTO sku (sku_id) VALUES (104);
INSERT INTO sku (sku_id) VALUES (105);

-- 모든 판매처의 원가가 동일한 SKU
INSERT INTO sku_channel_price VALUES (
    1, 101, 1, 8000, DATE '2026-01-01', NULL, TIMESTAMP '2026-01-01 00:00:00', 0
);
INSERT INTO sku_channel_price VALUES (
    2, 101, 2, 8000, DATE '2026-01-01', NULL, TIMESTAMP '2026-01-02 00:00:00', 0
);

-- 활성 원가가 충돌하면 가장 많이 사용된 원가를 선택한다.
INSERT INTO sku_channel_price VALUES (
    3, 102, 1, 9000, DATE '2026-01-01', NULL, TIMESTAMP '2026-02-01 00:00:00', 0
);
INSERT INTO sku_channel_price VALUES (
    4, 102, 2, 9000, DATE '2026-01-01', NULL, TIMESTAMP '2026-02-02 00:00:00', 0
);
INSERT INTO sku_channel_price VALUES (
    5, 102, 3, 9500, DATE '2026-01-01', NULL, TIMESTAMP '2026-02-03 00:00:00', 0
);

-- 현재 유효한 가격이 없으면 가장 최근 과거 가격의 원가를 선택한다.
INSERT INTO sku_channel_price VALUES (
    6, 103, 1, 11000, DATE '2024-01-01', DATE '2024-12-31', TIMESTAMP '2024-01-01 00:00:00', 0
);
INSERT INTO sku_channel_price VALUES (
    7, 103, 1, 12000, DATE '2025-01-01', DATE '2025-12-31', TIMESTAMP '2025-01-01 00:00:00', 0
);

-- 아직 적용되지 않은 미래 가격만 있는 SKU는 이관하지 않는다.
INSERT INTO sku_channel_price VALUES (
    8, 105, 1, 13000, DATE '2099-01-01', NULL, TIMESTAMP '2026-01-01 00:00:00', 0
);
