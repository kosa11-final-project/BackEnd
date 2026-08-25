DROP ALL OBJECTS;

CREATE TABLE category (
    category_id NUMBER PRIMARY KEY,
    category_name VARCHAR2(200),
    category_level NUMBER(2)
);
CREATE TABLE product (
    product_id NUMBER PRIMARY KEY,
    category_id NUMBER,
    product_name VARCHAR2(200),
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
CREATE TABLE strategy_case (
    strategy_case_id NUMBER PRIMARY KEY,
    sku_id NUMBER,
    created_by NUMBER,
    case_name VARCHAR2(200),
    case_status VARCHAR2(30),
    generation_stage VARCHAR2(30),
    result_expires_at TIMESTAMP,
    failure_code VARCHAR2(100),
    failure_message VARCHAR2(2000),
    completed_at TIMESTAMP,
    created_at TIMESTAMP,
    is_deleted NUMBER(1)
);

INSERT INTO category VALUES (1, '가공식품', 2);
INSERT INTO product VALUES (1, 1, '국산콩 두부', 'https://example.com/tofu.jpg');
INSERT INTO product VALUES (2, 1, '왕교자 만두', NULL);
INSERT INTO sku VALUES (1, 1, 'SKU_TOFU', '두부 300g');
INSERT INTO sku VALUES (2, 2, 'SKU-DUMPLING', '왕교자 1kg');
INSERT INTO app_user VALUES (7, '이주영');

-- 조회 대상: 처리 중, Redis 유효 완료, 실패 후 3일 이내
INSERT INTO strategy_case VALUES (
    101, 1, 7, '두부 전략 생성', 'GENERATING', 'FORECASTING',
    NULL, NULL, NULL, NULL, TIMESTAMP '2026-08-24 09:00:00', 0
);
INSERT INTO strategy_case VALUES (
    102, 1, 7, '두부 할인 전략', 'GENERATED', 'COMPARISON_READY',
    TIMESTAMP '2026-08-25 10:00:00', NULL, NULL, TIMESTAMP '2026-08-24 09:30:00',
    TIMESTAMP '2026-08-24 09:00:00', 0
);
INSERT INTO strategy_case VALUES (
    104, 2, 7, '만두 실패 전략', 'GENERATION_FAILED', 'STRATEGY_GENERATING',
    NULL, 'AI_TIMEOUT', '추천 생성 시간이 초과되었습니다.', TIMESTAMP '2026-08-22 10:00:00',
    TIMESTAMP '2026-08-22 09:00:00', 0
);

-- 조회 제외: Redis 만료, 생성/실패 후 3일 경과, 명시적 만료, 최종 선택, 삭제
INSERT INTO strategy_case VALUES (
    103, 1, 7, '만료된 생성 결과', 'GENERATED', 'COMPARISON_READY',
    TIMESTAMP '2026-08-24 09:59:59', NULL, NULL, TIMESTAMP '2026-08-21 09:00:00',
    TIMESTAMP '2026-08-21 08:00:00', 0
);
INSERT INTO strategy_case VALUES (
    105, 2, 7, '오래된 실패 결과', 'GENERATION_FAILED', 'FORECASTING',
    NULL, 'ML_ERROR', '수요예측 실패', TIMESTAMP '2026-08-21 09:59:59',
    TIMESTAMP '2026-08-21 09:00:00', 0
);
INSERT INTO strategy_case VALUES (
    106, 1, 7, '만료 상태', 'EXPIRED', 'COMPARISON_READY',
    TIMESTAMP '2026-08-20 10:00:00', NULL, NULL, TIMESTAMP '2026-08-17 10:00:00',
    TIMESTAMP '2026-08-17 09:00:00', 0
);
INSERT INTO strategy_case VALUES (
    107, 1, 7, '최종 선택 전략', 'READY_TO_EXECUTE', 'COMPARISON_READY',
    TIMESTAMP '2026-08-25 10:00:00', NULL, NULL, TIMESTAMP '2026-08-24 08:00:00',
    TIMESTAMP '2026-08-24 07:00:00', 0
);
INSERT INTO strategy_case VALUES (
    108, 1, 7, '삭제된 전략', 'GENERATING', 'FORECASTING',
    NULL, NULL, NULL, NULL, TIMESTAMP '2026-08-24 09:30:00', 1
);
INSERT INTO strategy_case VALUES (
    109, 1, 7, '오래된 생성 중 전략', 'GENERATING', 'FORECASTING',
    NULL, NULL, NULL, NULL, TIMESTAMP '2026-08-21 09:59:59', 0
);
INSERT INTO strategy_case VALUES (
    110, 1, 7, '정확히 3일 된 생성 중 전략', 'GENERATING', 'FORECASTING',
    NULL, NULL, NULL, NULL, TIMESTAMP '2026-08-21 10:00:00', 0
);
INSERT INTO strategy_case VALUES (
    111, 1, 7, '정확히 3일 된 실패 전략', 'GENERATION_FAILED', 'FORECASTING',
    NULL, 'ML_ERROR', '수요예측 실패', TIMESTAMP '2026-08-21 10:00:00',
    TIMESTAMP '2026-08-21 09:00:00', 0
);
