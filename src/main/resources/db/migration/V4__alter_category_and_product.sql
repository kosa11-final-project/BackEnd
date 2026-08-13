-- =========================================================
-- V4
-- 1. CATEGORY의 판매처/원천 구분 컬럼 제거
-- 2. PRODUCT에 대표 상품 이미지 URL 컬럼 추가
-- =========================================================

-- CATEGORY.source 관련 CHECK 제약조건 제거
ALTER TABLE category
    DROP CONSTRAINT ck_category_source;

-- CATEGORY.source 컬럼 제거
ALTER TABLE category
    DROP COLUMN source;

-- PRODUCT 대표 이미지 URL 컬럼 추가
ALTER TABLE product
    ADD image_url VARCHAR2(1000);

COMMENT ON COLUMN product.image_url IS
    'Representative product image URL';